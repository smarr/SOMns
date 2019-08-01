package som.primitives;

import java.util.concurrent.ForkJoinPool;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.Primitive;
import som.VM;
import som.interpreter.nodes.ExceptionSignalingNode;
import som.interpreter.nodes.nary.BinaryComplexOperation.BinarySystemOperation;
import som.interpreter.nodes.nary.TernaryExpressionNode.TernarySystemOperation;
import som.primitives.ObjectPrims.IsValue;
import som.primitives.arrays.ToArgumentsArrayFactory;
import som.primitives.arrays.ToArgumentsArrayNode;
import som.primitives.processes.ChannelPrimitives;
import som.primitives.processes.ChannelPrimitives.Process;
import som.primitives.processes.ChannelPrimitives.ReplayProcess;
import som.primitives.processes.ChannelPrimitives.TracingProcess;
import som.primitives.threading.TaskThreads.SomForkJoinTask;
import som.primitives.threading.TaskThreads.SomThreadTask;
import som.primitives.threading.TaskThreads.TracedForkJoinTask;
import som.primitives.threading.TaskThreads.TracedThreadTask;
import som.primitives.threading.ThreadingModule;
import som.vm.VmSettings;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;
import tools.concurrency.KomposTrace;
import tools.concurrency.Tags.ActivityCreation;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.debugger.entities.ActivityType;
import tools.debugger.entities.BreakpointType;
import tools.debugger.nodes.AbstractBreakpointNode;
import tools.debugger.session.Breakpoints;
import tools.replay.actors.ActorExecutionTrace;
import tools.replay.nodes.RecordEventNodes.RecordOneEvent;


public abstract class ActivitySpawn {

  private static SomForkJoinTask createTask(final Object[] argArray,
      final boolean stopOnRoot, final SBlock block, final SourceSection section) {
    SomForkJoinTask task;

    if (VmSettings.KOMPOS_TRACING) {
      task = new TracedForkJoinTask(argArray, stopOnRoot);
      KomposTrace.activityCreation(ActivityType.TASK, task.getId(),
          block.getMethod().getSignature(), section);
    } else {
      task = new SomForkJoinTask(argArray, stopOnRoot);
    }
    return task;
  }

  private static SomThreadTask createThread(final Object[] argArray,
      final boolean stopOnRoot, final SBlock block, final SourceSection section) {
    SomThreadTask thread;
    if (VmSettings.KOMPOS_TRACING) {
      thread = new TracedThreadTask(argArray, stopOnRoot);
      KomposTrace.activityCreation(ActivityType.THREAD, thread.getId(),
          block.getMethod().getSignature(), section);
    } else {
      thread = new SomThreadTask(argArray, stopOnRoot);
    }
    return thread;
  }

  private static Process createProcess(final SObjectWithClass obj,
      final SourceSection origin, final boolean stopOnRoot,
      final RecordOneEvent traceProcCreation) {
    if (VmSettings.REPLAY) {
      return new ReplayProcess(obj, stopOnRoot);
    } else if (VmSettings.KOMPOS_TRACING || VmSettings.ACTOR_TRACING) {
      TracingProcess result = new TracingProcess(obj, stopOnRoot);
      if (VmSettings.KOMPOS_TRACING) {
        KomposTrace.activityCreation(ActivityType.PROCESS,
            result.getId(), result.getProcObject().getSOMClass().getName(), origin);
      } else if (VmSettings.ACTOR_TRACING) {
        traceProcCreation.record(result.getId());
      }
      return result;
    } else {
      return new Process(obj);
    }
  }

  public static IsValue createIsValue() {
    return IsValue.createSubNode();
  }

  @GenerateNodeFactory
  @ImportStatic({ThreadingModule.class, ChannelPrimitives.class, ActivitySpawn.class})
  @Primitive(primitive = "threading:threadSpawn:")
  @Primitive(primitive = "threading:taskSpawn:")
  @Primitive(selector = "spawn:")
  public abstract static class SpawnPrim extends BinarySystemOperation {
    @CompilationFinal private ForkJoinPool forkJoinPool;
    @CompilationFinal private ForkJoinPool processesPool;
    @CompilationFinal private ForkJoinPool threadPool;

    /** Breakpoint info for triggering suspension on first execution of code in activity. */
    @Child protected AbstractBreakpointNode onExec;
    @Child protected ExceptionSignalingNode notAValue;
    @Child RecordOneEvent                   traceProcCreation =
        new RecordOneEvent(ActorExecutionTrace.PROCESS_CREATE);

    @Override
    public final SpawnPrim initialize(final VM vm) {
      super.initialize(vm);
      onExec = insert(Breakpoints.create(sourceSection, BreakpointType.ACTIVITY_ON_EXEC, vm));
      notAValue = insert(ExceptionSignalingNode.createNotAValueNode(sourceSection));

      forkJoinPool = vm.getForkJoinPool();
      processesPool = vm.getProcessPool();
      threadPool = vm.getThreadPool();

      return this;
    }

    @Specialization(guards = "clazz == TaskClass")
    @TruffleBoundary
    public final SomForkJoinTask spawnTask(final SClass clazz, final SBlock block) {
      SomForkJoinTask task = createTask(new Object[] {block},
          onExec.executeShouldHalt(), block, sourceSection);
      forkJoinPool.execute(task);
      return task;
    }

    @Specialization(guards = "clazz == ThreadClass")
    @TruffleBoundary
    public final SomThreadTask spawnThread(final SClass clazz, final SBlock block) {
      SomThreadTask thread = createThread(new Object[] {block},
          onExec.executeShouldHalt(), block, sourceSection);
      threadPool.execute(thread);
      return thread;
    }

    @Specialization(guards = "procMod == ProcessesModule")
    public final Object spawnProcess(final VirtualFrame frame, final SImmutableObject procMod,
        final SClass procCls, @Cached("createIsValue()") final IsValue isVal) {
      if (!isVal.executeBoolean(frame, procCls)) {
        notAValue.signal(procCls);
      }

      spawnProcess(procCls, traceProcCreation);
      return Nil.nilObject;
    }

    @TruffleBoundary
    private void spawnProcess(final SClass procCls, final RecordOneEvent traceProcCreation) {
      SSymbol sel = procCls.getMixinDefinition().getPrimaryFactorySelector();
      SInvokable disp = procCls.getMixinDefinition().getFactoryMethods().get(sel);
      SObjectWithClass obj = (SObjectWithClass) disp.invoke(new Object[] {procCls});

      processesPool.submit(createProcess(obj, sourceSection,
          onExec.executeShouldHalt(), traceProcCreation));
    }

    @Override
    protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
      if (tag == ActivityCreation.class ||
          tag == ExpressionBreakpoint.class ||
          tag == StatementTag.class) {
        return true;
      }
      return super.hasTag(tag);
    }
  }

  @GenerateNodeFactory
  @ImportStatic({ThreadingModule.class, ChannelPrimitives.class, ActivitySpawn.class})
  @NodeChild(value = "argArr", type = ToArgumentsArrayNode.class,
      executeWith = {"secondArg", "firstArg"})
  @Primitive(primitive = "threading:threadSpawn:with:",
      extraChild = ToArgumentsArrayFactory.class)
  @Primitive(primitive = "threading:taskSpawn:with:",
      extraChild = ToArgumentsArrayFactory.class)
  @Primitive(primitive = "proc:spawn:with:", extraChild = ToArgumentsArrayFactory.class)
  @Primitive(selector = "spawn:with:", extraChild = ToArgumentsArrayFactory.class)
  public abstract static class SpawnWithPrim extends TernarySystemOperation {
    @CompilationFinal private ForkJoinPool forkJoinPool;
    @CompilationFinal private ForkJoinPool processesPool;
    @CompilationFinal private ForkJoinPool threadPool;

    /** Breakpoint info for triggering suspension on first execution of code in activity. */
    @Child protected AbstractBreakpointNode onExec;

    @Child protected ExceptionSignalingNode notAValue;

    @Child RecordOneEvent traceProcCreation =
        new RecordOneEvent(ActorExecutionTrace.PROCESS_CREATE);

    @Override
    public final SpawnWithPrim initialize(final VM vm) {
      super.initialize(vm);
      onExec = insert(Breakpoints.create(sourceSection, BreakpointType.ACTIVITY_ON_EXEC, vm));
      notAValue = insert(ExceptionSignalingNode.createNotAValueNode(sourceSection));

      forkJoinPool = vm.getForkJoinPool();
      processesPool = vm.getProcessPool();
      threadPool = vm.getThreadPool();

      return this;
    }

    @Specialization(guards = "clazz == TaskClass")
    @TruffleBoundary
    public SomForkJoinTask spawnTask(final SClass clazz, final SBlock block,
        final SArray somArgArr, final Object[] argArr) {
      SomForkJoinTask task = createTask(argArr,
          onExec.executeShouldHalt(), block, sourceSection);
      forkJoinPool.execute(task);
      return task;
    }

    @Specialization(guards = "clazz == ThreadClass")
    @TruffleBoundary
    public SomThreadTask spawnThread(final SClass clazz, final SBlock block,
        final SArray somArgArr, final Object[] argArr) {
      SomThreadTask thread = createThread(argArr,
          onExec.executeShouldHalt(), block, sourceSection);
      threadPool.execute(thread);
      return thread;
    }

    @Specialization(guards = "procMod == ProcessesModule")
    public final Object spawnProcess(final VirtualFrame frame, final SImmutableObject procMod,
        final SClass procCls, final SArray arg, final Object[] argArr,
        @Cached("createIsValue()") final IsValue isVal) {
      if (!isVal.executeBoolean(frame, procCls)) {
        notAValue.signal(procCls);
      }

      spawnProcess(procCls, argArr, traceProcCreation);
      return Nil.nilObject;
    }

    @TruffleBoundary
    private void spawnProcess(final SClass procCls, final Object[] argArr,
        final RecordOneEvent traceProcCreation) {
      SSymbol sel = procCls.getMixinDefinition().getPrimaryFactorySelector();
      SInvokable disp = procCls.getMixinDefinition().getFactoryMethods().get(sel);
      SObjectWithClass obj = (SObjectWithClass) disp.invoke(argArr);

      processesPool.submit(createProcess(obj, sourceSection,
          onExec.executeShouldHalt(), traceProcCreation));
    }

    @Override
    protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
      if (tag == ActivityCreation.class ||
          tag == ExpressionBreakpoint.class ||
          tag == StatementTag.class) {
        return true;
      }
      return super.hasTag(tag);
    }
  }
}
