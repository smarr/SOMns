package som.primitives.processes;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.compiler.AccessModifier;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.SomLanguage;
import som.interpreter.actors.SuspendExecutionNodeGen;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.processes.SChannel;
import som.interpreter.processes.SChannel.SChannelInput;
import som.interpreter.processes.SChannel.SChannelOutput;
import som.primitives.ObjectPrims.IsValue;
import som.primitives.Primitive;
import som.vm.Activity;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vm.constants.KernelObj;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObjectWithClass;
import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.Tags.ChannelRead;
import tools.concurrency.Tags.ChannelWrite;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.concurrency.TracingActivityThread;
import tools.debugger.WebDebugger;
import tools.debugger.entities.ActivityType;
import tools.debugger.entities.BreakpointType;
import tools.debugger.nodes.AbstractBreakpointNode;
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

  public static final class ProcessThreadFactory implements ForkJoinWorkerThreadFactory {
    @Override
    public ForkJoinWorkerThread newThread(final ForkJoinPool pool) {
      return new ProcessThread(pool);
    }
  }

  public static final class ProcessThread extends TracingActivityThread {
    private Process current;

    ProcessThread(final ForkJoinPool pool) { super(pool); }

    @Override
    public Activity getActivity() {
      return current;
    }

    @Override
    public long getCurrentMessageId() {
      return -1;
    }
  }

  public static class Process implements Activity, Runnable {
    private final SObjectWithClass obj;
    private final boolean stopOnRootNode;

    public Process(final SObjectWithClass obj, final boolean stopOnRootNode) {
      this.obj = obj;
      this.stopOnRootNode = stopOnRootNode;
    }

    @Override
    public ActivityType getType() { return ActivityType.PROCESS; }

    @Override
    public void run() {
      ((ProcessThread) Thread.currentThread()).current = this;

      try {
        SInvokable disp = (SInvokable) obj.getSOMClass().lookupMessage(
            Symbols.symbolFor("run"), AccessModifier.PROTECTED);

        if (VmSettings.TRUFFLE_DEBUGGER_ENABLED && stopOnRootNode) {
          WebDebugger dbg = SomLanguage.getVM(disp.getInvokable()).getWebDebugger();
          dbg.prepareSteppingUntilNextRootNode();
        }
        disp.invoke(new Object[] {obj});
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }

    @Override
    public long getId() { return 0; }

    public SObjectWithClass getProcObject() {
      return obj;
    }

    @Override
    public String getName() {
      return obj.getSOMClass().getName().getString();
    }
  }

  public static class TracingProcess extends Process {
    protected final long processId;

    public TracingProcess(final SObjectWithClass obj, final boolean stopOnRootNode) {
      super(obj, stopOnRootNode);
      assert Thread.currentThread() instanceof TracingActivityThread;
      processId = ((TracingActivityThread) Thread.currentThread()).generateActivityId();
    }

    @Override
    public void run() {
      try {
        super.run();
      } finally {
        if (VmSettings.ACTOR_TRACING) {
          ActorExecutionTrace.processCompletion(this);
        }
      }
    }

    @Override
    public long getId() { return processId; }
  }

  @Primitive(primitive = "procOut:")
  @GenerateNodeFactory
  public abstract static class OutPrim extends UnaryExpressionNode {
    public OutPrim(final boolean eagerlyWrapped, final SourceSection source) { super(eagerlyWrapped, source); }

    @Specialization
    public static final SChannelOutput getOut(final SChannel channel) {
      return channel.out;
    }
  }

  @Primitive(primitive = "procRead:", selector = "read", requiresContext = true)
  @GenerateNodeFactory
  public abstract static class ReadPrim extends UnaryExpressionNode {
    /** Halt execution when triggered by breakpoint on write end. */
    @Child protected UnaryExpressionNode haltNode;

    /** Breakpoint info for triggering suspension after write. */
    @Child protected AbstractBreakpointNode afterWrite;

    public ReadPrim(final boolean eagerlyWrapped, final SourceSection source, final VM vm) {
      super(eagerlyWrapped, source);
      haltNode = SuspendExecutionNodeGen.create(false, sourceSection, null);
      afterWrite = insert(Breakpoints.createOpposite(
          source, vm, BreakpointType.CHANNEL_AFTER_SEND));
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

  @Primitive(primitive = "procWrite:val:", selector = "write:", requiresContext = true)
  @GenerateNodeFactory
  public abstract static class WritePrim extends BinaryComplexOperation {
    @Child protected IsValue isVal;

    /** Halt execution when triggered by breakpoint on write end. */
    @Child protected UnaryExpressionNode haltNode;

    /** Breakpoint info for triggering suspension after read. */
    @Child protected AbstractBreakpointNode afterRead;

    public WritePrim(final boolean eagerlyWrapped, final SourceSection source, final VM vm) {
      super(eagerlyWrapped, source);
      isVal     = IsValue.createSubNode();
      haltNode  = SuspendExecutionNodeGen.create(false, sourceSection, null);
      afterRead = insert(Breakpoints.createOpposite(
          source, vm, BreakpointType.CHANNEL_AFTER_RCV));
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
    public final SChannel newChannel(final Object module) {
      SChannel result = SChannel.create();

      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.channelCreation(result, sourceSection);
      }
      return result;
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
