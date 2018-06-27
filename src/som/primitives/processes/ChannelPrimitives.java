package som.primitives.processes;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import bd.primitives.Primitive;
import som.VM;
import som.compiler.AccessModifier;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.SomLanguage;
import som.interpreter.actors.SuspendExecutionNodeGen;
import som.interpreter.nodes.ExceptionSignalingNode;
import som.interpreter.nodes.nary.BinaryComplexOperation.BinarySystemOperation;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySystemOperation;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.interpreter.processes.SChannel;
import som.interpreter.processes.SChannel.SChannelInput;
import som.interpreter.processes.SChannel.SChannelOutput;
import som.primitives.ObjectPrims.IsValue;
import som.vm.Activity;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObjectWithClass;
import tools.concurrency.MedeorTrace;
import tools.concurrency.Tags.ChannelRead;
import tools.concurrency.Tags.ChannelWrite;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.concurrency.TracingActivityThread;
import tools.debugger.WebDebugger;
import tools.debugger.entities.ActivityType;
import tools.debugger.entities.BreakpointType;
import tools.debugger.entities.PassiveEntityType;
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
    Channel = null;
    ChannelId = null;
    In = null;
    InId = null;
    Out = null;
    OutId = null;
  }

  public static final class ProcessThreadFactory implements ForkJoinWorkerThreadFactory {
    @Override
    public ForkJoinWorkerThread newThread(final ForkJoinPool pool) {
      return new ProcessThread(pool);
    }
  }

  public static final class ProcessThread extends TracingActivityThread {
    private Process current;

    ProcessThread(final ForkJoinPool pool) {
      super(pool);
    }

    @Override
    public Activity getActivity() {
      return current;
    }
  }

  public static class Process implements Activity, Runnable {
    private final SObjectWithClass obj;

    public Process(final SObjectWithClass obj) {
      this.obj = obj;
    }

    @Override
    public ActivityType getType() {
      return ActivityType.PROCESS;
    }

    protected void beforeExec(final SInvokable disp) {}

    @Override
    public void run() {
      ((ProcessThread) Thread.currentThread()).current = this;
      ObjectTransitionSafepoint.INSTANCE.register();

      try {
        SInvokable disp = (SInvokable) obj.getSOMClass().lookupMessage(
            Symbols.symbolFor("run"), AccessModifier.PROTECTED);

        beforeExec(disp);
        disp.invoke(new Object[] {obj});
      } catch (Throwable t) {
        t.printStackTrace();
      } finally {
        ObjectTransitionSafepoint.INSTANCE.unregister();
      }
    }

    public SObjectWithClass getProcObject() {
      return obj;
    }

    @Override
    public String getName() {
      return obj.getSOMClass().getName().getString();
    }

    @Override
    public void setStepToNextTurn(final boolean val) {
      throw new UnsupportedOperationException(
          "Step to next turn is not supported " +
              "for processes. This code should never be reached.");
    }
  }

  public static class TracingProcess extends Process {
    protected final long processId;
    private int          nextTraceBufferId;

    private final boolean stopOnRootNode;
    private boolean       stopOnJoin;

    public TracingProcess(final SObjectWithClass obj, final boolean stopOnRootNode) {
      super(obj);
      this.stopOnRootNode = stopOnRootNode;
      processId = TracingActivityThread.newEntityId();
    }

    @Override
    public int getNextTraceBufferId() {
      int result = nextTraceBufferId;
      nextTraceBufferId += 1;
      return result;
    }

    @Override
    protected void beforeExec(final SInvokable disp) {
      if (VmSettings.TRUFFLE_DEBUGGER_ENABLED && stopOnRootNode) {
        WebDebugger dbg = SomLanguage.getVM(disp.getInvokable()).getWebDebugger();
        dbg.prepareSteppingUntilNextRootNode();
      }

      MedeorTrace.currentActivity(this);
    }

    @Override
    public void run() {
      try {
        super.run();
      } finally {
        assert VmSettings.MEDEOR_TRACING;
        MedeorTrace.activityCompletion(ActivityType.PROCESS);
      }
    }

    @Override
    public long getId() {
      return processId;
    }

    @Override
    public void setStepToJoin(final boolean val) {
      stopOnJoin = val;
    }
  }

  @Primitive(primitive = "procOut:")
  @GenerateNodeFactory
  public abstract static class OutPrim extends UnaryExpressionNode {
    @Specialization
    public static final SChannelOutput getOut(final SChannel channel) {
      return channel.out;
    }
  }

  @Primitive(primitive = "procRead:", selector = "read")
  @GenerateNodeFactory
  public abstract static class ReadPrim extends UnarySystemOperation {
    /** Halt execution when triggered by breakpoint on write end. */
    @Child protected UnaryExpressionNode haltNode;

    /** Breakpoint info for triggering suspension after write. */
    @Child protected AbstractBreakpointNode afterWrite;

    @Override
    public final ReadPrim initialize(final VM vm) {
      super.initialize(vm);
      haltNode = SuspendExecutionNodeGen.create(0, null).initialize(sourceSection);
      afterWrite = insert(
          Breakpoints.create(sourceSection, BreakpointType.CHANNEL_AFTER_SEND, vm));
      return this;
    }

    @Specialization
    public final Object read(final VirtualFrame frame, final SChannelInput in) {
      try {
        Object result = in.readAndSuspendWriter(afterWrite.executeShouldHalt());
        if (in.shouldBreakAfterRead()) {
          haltNode.executeEvaluated(frame, result);
        }
        return result;
      } catch (InterruptedException e) {
        CompilerDirectives.transferToInterpreter();
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
  public abstract static class WritePrim extends BinarySystemOperation {
    @Child protected IsValue isVal = IsValue.createSubNode();

    /** Halt execution when triggered by breakpoint on write end. */
    @Child protected UnaryExpressionNode haltNode;

    /** Breakpoint info for triggering suspension after read. */
    @Child protected AbstractBreakpointNode afterRead;

    @Child protected ExceptionSignalingNode notAValue;

    @Override
    public final WritePrim initialize(final VM vm) {
      super.initialize(vm);
      haltNode = insert(SuspendExecutionNodeGen.create(0, null).initialize(sourceSection));
      afterRead =
          insert(Breakpoints.create(sourceSection, BreakpointType.CHANNEL_AFTER_RCV, vm));
      notAValue = insert(ExceptionSignalingNode.createNotAValueNode(sourceSection));
      return this;
    }

    @Specialization
    public final Object write(final VirtualFrame frame, final SChannelOutput out,
        final Object val) {
      if (!isVal.executeEvaluated(val)) {
        notAValue.signal(val);
      }
      try {
        out.writeAndSuspendReader(val, afterRead.executeShouldHalt());
        if (out.shouldBreakAfterWrite()) {
          haltNode.executeEvaluated(frame, val);
        }
      } catch (InterruptedException e) {
        CompilerDirectives.transferToInterpreter();
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
    @Specialization
    public static final SChannelInput getInt(final SChannel channel) {
      return channel.in;
    }
  }

  @Primitive(primitive = "procChannelNew:")
  @GenerateNodeFactory
  public abstract static class ChannelNewPrim extends UnaryExpressionNode {
    @Specialization
    public final SChannel newChannel(final Object module) {
      SChannel result = SChannel.create();

      if (VmSettings.MEDEOR_TRACING) {
        MedeorTrace.passiveEntityCreation(PassiveEntityType.CHANNEL,
            result.getId(), MedeorTrace.getPrimitiveCaller(sourceSection));
      }
      return result;
    }
  }

  @Primitive(primitive = "procClassChannel:in:out:")
  @GenerateNodeFactory
  public abstract static class SetChannelClasses extends TernaryExpressionNode {
    @Specialization
    public static final Object set(final SClass channel, final SClass in, final SClass out) {
      Channel = channel;
      ChannelId = channel.getMixinDefinition().getMixinId();
      In = in;
      InId = in.getMixinDefinition().getMixinId();
      Out = out;
      OutId = out.getMixinDefinition().getMixinId();
      return channel;
    }
  }

  @Primitive(primitive = "procModule:")
  @GenerateNodeFactory
  public abstract static class SetChannelModule extends UnaryExpressionNode {
    @Specialization
    public static final SImmutableObject setModule(final SImmutableObject module) {
      ProcessesModule = module;
      return module;
    }
  }
}
