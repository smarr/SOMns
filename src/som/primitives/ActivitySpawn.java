package som.primitives;

import java.util.concurrent.ForkJoinPool;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.primitives.ObjectPrims.IsValue;
import som.primitives.arrays.ToArgumentsArrayNode;
import som.primitives.arrays.ToArgumentsArrayNodeFactory;
import som.primitives.processes.ChannelPrimitives;
import som.primitives.processes.ChannelPrimitives.Process;
import som.primitives.processes.ChannelPrimitives.TracingProcess;
import som.primitives.threading.TaskThreads.SomForkJoinTask;
import som.primitives.threading.TaskThreads.TracedForkJoinTask;
import som.primitives.threading.ThreadPrimitives.SomThread;
import som.primitives.threading.ThreadingModule;
import som.vm.ActivityThread;
import som.vm.VmSettings;
import som.vm.constants.KernelObj;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;
import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.Tags.ActivityCreation;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.debugger.SteppingStrategy;
import tools.debugger.nodes.AbstractBreakpointNode;
import tools.debugger.session.Breakpoints;

public abstract class ActivitySpawn {

  private static SomForkJoinTask createTask(final Object[] argArray) {
    if (VmSettings.ACTOR_TRACING) {
      return new TracedForkJoinTask(argArray);
    } else {
      return new SomForkJoinTask(argArray);
    }
  }

  private static Process createProcess(final SObjectWithClass obj,
      final SourceSection origin, final boolean stopOnRoot) {
    if (VmSettings.ACTOR_TRACING) {
      TracingProcess result = new TracingProcess(obj, stopOnRoot);
      ActorExecutionTrace.processCreation(result, origin);
      return result;
    } else {
      return new Process(obj, stopOnRoot);
    }
  }

  public static IsValue createIsValue() {
    return IsValue.createSubNode();
  }

  public static boolean stopOnRoot() {
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      SteppingStrategy strategy = ActivityThread.steppingStrategy();
      if (strategy == null) {
        return false;
      }
      return strategy.handleSpawn();
    } else {
      return false;
    }
  }

  @GenerateNodeFactory
  @ImportStatic({ThreadingModule.class, ChannelPrimitives.class, ActivitySpawn.class})
  @Primitive(primitive = "threading:threadSpawn:", requiresContext = true)
  @Primitive(primitive = "threading:taskSpawn:", requiresContext = true)
  @Primitive(selector = "spawn:", requiresContext = true)
  public abstract static class SpawnPrim extends BinaryComplexOperation {
    private final ForkJoinPool forkJoinPool;
    private final ForkJoinPool processesPool;

    /** Breakpoint info for triggering suspension on first execution of code in activity. */
    @Child protected AbstractBreakpointNode onExec;

    public SpawnPrim(final boolean ew, final SourceSection s, final VM vm) {
      super(ew, s);
      this.forkJoinPool  = vm.getForkJoinPool();
      this.processesPool = vm.getProcessPool();
      this.onExec = insert(Breakpoints.createOnExec(s, vm));
    }

    @Specialization(guards = "clazz == TaskClass")
    @TruffleBoundary
    public final SomForkJoinTask spawnTask(final SClass clazz, final SBlock block) {
      SomForkJoinTask task = createTask(new Object[] {block});
      forkJoinPool.execute(task);

      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.taskSpawn(block.getMethod(), task.getId(), sourceSection);
      }
      return task;
    }

    @Specialization(guards = "clazz == ThreadClass")
    public final Thread spawnThread(final SClass clazz, final SBlock block) {
      SomThread thread = new SomThread(block, block);
      thread.start();
      return thread;
    }

    @Specialization(guards = "procMod == ProcessesModule")
    @TruffleBoundary
    public final Object spawnProcess(final SImmutableObject procMod,
        final SClass procCls, @Cached("createIsValue()") final IsValue isVal) {
      if (!isVal.executeEvaluated(procCls)) {
        KernelObj.signalException("signalNotAValueWith:", procCls);
      }

      SSymbol sel = procCls.getMixinDefinition().getPrimaryFactorySelector();
      SInvokable disp = procCls.getMixinDefinition().getFactoryMethods().get(sel);
      SObjectWithClass obj = (SObjectWithClass) disp.invoke(new Object[] {procCls});

      processesPool.submit(createProcess(obj, sourceSection,
          onExec.executeCheckIsSetAndEnabled() || stopOnRoot()));
      return Nil.nilObject;
    }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == ActivityCreation.class || tag == ExpressionBreakpoint.class) {
        return true;
      }
      return super.isTaggedWith(tag);
    }
  }

  @GenerateNodeFactory
  @ImportStatic({ThreadingModule.class, ChannelPrimitives.class, ActivitySpawn.class})
  @NodeChild(value = "argArr", type = ToArgumentsArrayNode.class,
    executeWith = {"secondArg", "firstArg"})
  @Primitive(primitive = "threading:threadSpawn:with:",
    extraChild = ToArgumentsArrayNodeFactory.class, requiresContext = true)
  @Primitive(primitive = "threading:taskSpawn:with:",
    extraChild = ToArgumentsArrayNodeFactory.class, requiresContext = true)
  @Primitive(primitive = "proc:spawn:with:",
    extraChild = ToArgumentsArrayNodeFactory.class, requiresContext = true)
  @Primitive(selector = "spawn:with:",
  extraChild = ToArgumentsArrayNodeFactory.class, requiresContext = true)
  public abstract static class SpawnWithPrim extends TernaryExpressionNode {
    private final ForkJoinPool forkJoinPool;
    private final ForkJoinPool processesPool;

    /** Breakpoint info for triggering suspension on first execution of code in activity. */
    @Child protected AbstractBreakpointNode onExec;

    public SpawnWithPrim(final boolean ew, final SourceSection s, final VM vm) {
      super(ew, s);
      this.forkJoinPool  = vm.getForkJoinPool();
      this.processesPool = vm.getProcessPool();
      this.onExec = insert(Breakpoints.createOnExec(s, vm));
    }

    @Specialization(guards = "clazz == TaskClass")
    public SomForkJoinTask spawnTask(final SClass clazz, final SBlock block,
        final SArray somArgArr, final Object[] argArr) {
      SomForkJoinTask task = createTask(argArr);
      forkJoinPool.execute(task);

      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.taskSpawn(block.getMethod(), task.getId(), sourceSection);
      }
      return task;
    }

    @Specialization(guards = "clazz == ThreadClass")
    public Thread spawnThread(final SClass clazz, final SBlock block,
        final SArray somArgArr, final Object[] argArr) {
      SomThread thread = new SomThread(block, argArr);
      thread.start();
      return thread;
    }

    @Specialization(guards = "procMod == ProcessesModule")
    @TruffleBoundary
    public final Object spawnProcess(final SImmutableObject procMod,
        final SClass procCls, final SArray arg, final Object[] argArr,
        @Cached("createIsValue()") final IsValue isVal) {
      if (!isVal.executeEvaluated(procCls)) {
        KernelObj.signalException("signalNotAValueWith:", procCls);
      }

      SSymbol sel = procCls.getMixinDefinition().getPrimaryFactorySelector();
      SInvokable disp = procCls.getMixinDefinition().getFactoryMethods().get(sel);
      SObjectWithClass obj = (SObjectWithClass) disp.invoke(argArr);

      processesPool.submit(createProcess(obj, sourceSection,
          onExec.executeCheckIsSetAndEnabled() || stopOnRoot()));
      return Nil.nilObject;
    }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == ActivityCreation.class || tag == ExpressionBreakpoint.class) {
        return true;
      }
      return super.isTaggedWith(tag);
    }
  }
}
