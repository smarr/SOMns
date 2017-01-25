package som.primitives.processes;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.compiler.AccessModifier;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.actors.Actor.UncaughtExceptions;
import som.interpreter.actors.SuspendExecutionNodeGen;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.processes.SChannel;
import som.interpreter.processes.SChannel.SChannelInput;
import som.interpreter.processes.SChannel.SChannelOutput;
import som.primitives.ObjectPrims.IsValue;
import som.primitives.Primitive;
import som.primitives.arrays.ToArgumentsArrayNode;
import som.primitives.arrays.ToArgumentsArrayNodeFactory;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vm.constants.KernelObj;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;
import tools.SourceCoordinate;
import tools.SourceCoordinate.FullSourceCoordinate;
import tools.concurrency.Tags.ChannelRead;
import tools.concurrency.Tags.ChannelWrite;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.debugger.nodes.AbstractBreakpointNode;
import tools.debugger.nodes.BreakpointNodeGen;
import tools.debugger.nodes.DisabledBreakpointNode;
import tools.debugger.session.Breakpoints;


public abstract class ChannelPrimitives {

  @CompilationFinal public static SImmutableObject  ProcessesModule;
  @CompilationFinal public static SClass            Channel;
  @CompilationFinal public static MixinDefinitionId ChannelId;
  @CompilationFinal public static SClass            In;
  @CompilationFinal public static MixinDefinitionId InId;
  @CompilationFinal public static SClass            Out;
  @CompilationFinal public static MixinDefinitionId OutId;

  public static void resetClassReferences() {
    Channel = null; ChannelId = null;
    In      = null; InId      = null;
    Out     = null; OutId     = null;
  }

  private static final class ProcessThreadFactory implements ForkJoinWorkerThreadFactory {
    @Override
    public ForkJoinWorkerThread newThread(final ForkJoinPool pool) {
      return new ProcessThread(pool);
    }
  }

  public static final class ProcessThread extends ForkJoinWorkerThread {
    ProcessThread(final ForkJoinPool pool) { super(pool); }
  }

  private static final class Process implements Runnable {
    private final SObjectWithClass obj;

    Process(final SObjectWithClass obj) {
      this.obj = obj;
    }

    @Override
    public void run() {
      SInvokable disp = (SInvokable) obj.getSOMClass().lookupMessage(
          Symbols.symbolFor("run"), AccessModifier.PROTECTED);
      disp.invoke(obj);
    }
  }

  private static final ForkJoinPool processesPool = new ForkJoinPool(
      VmSettings.NUM_THREADS, new ProcessThreadFactory(), new UncaughtExceptions(), true);

  @Primitive(primitive = "procOut:")
  @GenerateNodeFactory
  public abstract static class OutPrim extends UnaryExpressionNode {
    public OutPrim(final boolean eagerlyWrapped, final SourceSection source) { super(eagerlyWrapped, source); }

    @Specialization
    public static final SChannelOutput getOut(final SChannel channel) {
      return channel.out;
    }
  }

  @Primitive(primitive = "procSpawn:with:")
  @GenerateNodeFactory
  public abstract static class SpawnProcess extends BinaryComplexOperation {
    @Child protected ToArgumentsArrayNode toArgs;
    @Child protected IsValue isVal;

    public SpawnProcess(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
      toArgs = ToArgumentsArrayNodeFactory.create(null, null);
      isVal  = IsValue.createSubNode();
    }

    @Specialization
    @TruffleBoundary
    public final Object spawn(final SClass procCls, final SArray args) {
      if (!isVal.executeEvaluated(procCls)) {
        KernelObj.signalException("signalNotAValueWith:", procCls);
      }

      SSymbol sel = procCls.getMixinDefinition().getPrimaryFactorySelector();
      SInvokable disp = procCls.getMixinDefinition().getFactoryMethods().get(sel);
      SObjectWithClass obj = (SObjectWithClass) disp.invoke(toArgs.executedEvaluated(args, procCls));

      processesPool.submit(new Process(obj));
      return Nil.nilObject;
    }
  }

  @Primitive(primitive = "procRead:", selector = "read")
  @GenerateNodeFactory
  public abstract static class ReadPrim extends UnaryExpressionNode {
    /** Halt execution when triggered by breakpoint on write end. */
    @Child protected UnaryExpressionNode haltNode;

    /** Breakpoint info for triggering suspension after write. */
    @Child protected AbstractBreakpointNode afterWrite;

    public ReadPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
      haltNode = SuspendExecutionNodeGen.create(false, sourceSection, null);

      if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
        Breakpoints bpCatalog = VM.getWebDebugger().getBreakpoints();
        FullSourceCoordinate coord = SourceCoordinate.create(source);
        this.afterWrite = insert(BreakpointNodeGen.create(bpCatalog.getOppositeBreakpoint(coord)));
      } else {
        this.afterWrite = insert(new DisabledBreakpointNode());
      }
    }

    @Specialization
    public final Object read(final VirtualFrame frame, final SChannelInput in) {
      try {
        Object result = in.readAndSuspendWriter(afterWrite.executeCheckIsSetAndEnabled());
        if (in.shouldBreakAfterRead()) {
          haltNode.executeEvaluated(frame, result);
        }
        return result;
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == ChannelRead.class || tag == ExpressionBreakpoint.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }
  }

  @Primitive(primitive = "procWrite:val:", selector = "write:")
  @GenerateNodeFactory
  public abstract static class WritePrim extends BinaryComplexOperation {
    @Child protected IsValue isVal;

    /** Halt execution when triggered by breakpoint on write end. */
    @Child protected UnaryExpressionNode haltNode;

    /** Breakpoint info for triggering suspension after read. */
    @Child protected AbstractBreakpointNode afterRead;

    public WritePrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
      isVal = IsValue.createSubNode();

      haltNode = SuspendExecutionNodeGen.create(false, sourceSection, null);

      if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
        Breakpoints bpCatalog = VM.getWebDebugger().getBreakpoints();
        FullSourceCoordinate coord = SourceCoordinate.create(source);
        this.afterRead = insert(BreakpointNodeGen.create(bpCatalog.getOppositeBreakpoint(coord)));
      } else {
        this.afterRead = insert(new DisabledBreakpointNode());
      }
    }

    @Specialization
    public final Object write(final VirtualFrame frame, final SChannelOutput out,
        final Object val) {
      if (!isVal.executeEvaluated(val)) {
        KernelObj.signalException("signalNotAValueWith:", val);
      }
      try {
        out.writeAndSuspendReader(val, afterRead.executeCheckIsSetAndEnabled());
        if (out.shouldBreakAfterWrite()) {
          haltNode.executeEvaluated(frame, val);
        }
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      return val;
    }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == ChannelWrite.class || tag == ExpressionBreakpoint.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }
  }

  @Primitive(primitive = "procIn:")
  @GenerateNodeFactory
  public abstract static class InPrim extends UnaryExpressionNode {
    public InPrim(final boolean eagerlyWrapped, final SourceSection source) { super(eagerlyWrapped, source); }

    @Specialization
    public static final SChannelInput getInt(final SChannel channel) {
      return channel.in;
    }
  }

  @Primitive(primitive = "procChannelNew:")
  @GenerateNodeFactory
  public abstract static class ChannelNewPrim extends UnaryExpressionNode {
    public ChannelNewPrim(final boolean eagerlyWrapped, final SourceSection source) { super(eagerlyWrapped, source); }

    @Specialization
    public static final SChannel newChannel(final Object module) {
      return new SChannel();
    }
  }

  @Primitive(primitive = "procClassChannel:in:out:")
  @GenerateNodeFactory
  public abstract static class SetChannelClasses extends TernaryExpressionNode {
    public SetChannelClasses(final boolean eagerlyWrapped, final SourceSection source) { super(eagerlyWrapped, source); }

    @Specialization
    public static final Object set(final SClass channel, final SClass in, final SClass out) {
      Channel   = channel;
      ChannelId = channel.getMixinDefinition().getMixinId();
      In        = in;
      InId      = in.getMixinDefinition().getMixinId();
      Out       = out;
      OutId     = out.getMixinDefinition().getMixinId();
      return channel;
    }
  }

  @Primitive(primitive = "procModule:")
  @GenerateNodeFactory
  public abstract static class SetChannelModule extends UnaryExpressionNode {
    public SetChannelModule(final boolean eagerlyWrapped, final SourceSection source) { super(eagerlyWrapped, source); }

    @Specialization
    public static final SImmutableObject setModule(final SImmutableObject module) {
      ProcessesModule = module;
      return module;
    }
  }
}
