package somns.primitives;

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
import somns.VM;
import somns.interpreter.nodes.ExceptionSignalingNode;
import somns.interpreter.nodes.nary.BinaryComplexOperation.BinarySystemOperation;
import somns.interpreter.nodes.nary.TernaryExpressionNode.TernarySystemOperation;
import somns.primitives.ObjectPrims.IsValue;
import somns.primitives.arrays.ToArgumentsArrayFactory;
import somns.primitives.arrays.ToArgumentsArrayNode;
import somns.primitives.processes.ChannelPrimitives;
import somns.primitives.processes.ChannelPrimitives.Process;
import somns.primitives.processes.ChannelPrimitives.ReplayProcess;
import somns.primitives.processes.ChannelPrimitives.TracingProcess;
import somns.primitives.threading.ThreadingModule;
import somns.primitives.threading.TaskThreads.ReplayForkJoinTask;
import somns.primitives.threading.TaskThreads.ReplayThreadTask;
import somns.primitives.threading.TaskThreads.SomForkJoinTask;
import somns.primitives.threading.TaskThreads.SomThreadTask;
import somns.primitives.threading.TaskThreads.TracedForkJoinTask;
import somns.primitives.threading.TaskThreads.TracedThreadTask;
import somns.vm.VmSettings;
import somns.vm.constants.Nil;
import somns.vmobjects.SArray;
import somns.vmobjects.SBlock;
import somns.vmobjects.SClass;
import somns.vmobjects.SInvokable;
import somns.vmobjects.SObjectWithClass;
import somns.vmobjects.SSymbol;
import somns.vmobjects.SObject.SImmutableObject;
import tools.concurrency.KomposTrace;
import tools.concurrency.Tags.ActivityCreation;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.debugger.entities.ActivityType;
import tools.debugger.entities.BreakpointType;
import tools.debugger.nodes.AbstractBreakpointNode;
import tools.debugger.session.Breakpoints;
import tools.replay.TraceRecord;
import tools.replay.nodes.RecordEventNodes.RecordOneEvent;


public abstract class ActivitySpawn {

  private static SomForkJoinTask createTask(final Object[] argArray,
      final boolean stopOnRoot, final SBlock block, final SourceSection section,
      final RecordOneEvent traceThreadCreation, final VM vm) {
    SomForkJoinTask task;

    if (VmSettings.REPLAY) {
      return new ReplayForkJoinTask(argArray, stopOnRoot, vm);
    } else if (VmSettings.KOMPOS_TRACING || VmSettings.UNIFORM_TRACING) {
      task = new TracedForkJoinTask(argArray, stopOnRoot, vm);

      if (VmSettings.KOMPOS_TRACING) {
        KomposTrace.activityCreation(ActivityType.TASK, task.getId(),
            block.getMethod().getSignature(), section);
      } else if (VmSettings.UNIFORM_TRACING) {
        traceThreadCreation.record(task.getId());
      }
    } else {
      task = new SomForkJoinTask(argArray, stopOnRoot);
    }
    return task;
  }

  private static SomThreadTask createThread(final Object[] argArray,
      final boolean stopOnRoot, final SBlock block, final SourceSection section,
      final RecordOneEvent traceThreadCreation, final VM vm) {
    SomThreadTask thread;

    if (VmSettings.REPLAY) {
      return new ReplayThreadTask(argArray, stopOnRoot, vm);
    } else if (VmSettings.KOMPOS_TRACING || VmSettings.UNIFORM_TRACING) {
      thread = new TracedThreadTask(argArray, stopOnRoot, vm);

      if (VmSettings.KOMPOS_TRACING) {
        KomposTrace.activityCreation(ActivityType.THREAD, thread.getId(),
            block.getMethod().getSignature(), section);
      } else if (VmSettings.UNIFORM_TRACING) {
        traceThreadCreation.record(thread.getId());
      }
    } else {
      thread = new SomThreadTask(argArray, stopOnRoot);
    }
    return thread;
  }

  private static Process createProcess(final SObjectWithClass obj,
      final SourceSection origin, final boolean stopOnRoot,
      final RecordOneEvent traceProcCreation, final VM vm) {
    if (VmSettings.REPLAY) {
      return new ReplayProcess(obj, stopOnRoot, vm);
    } else if (VmSettings.KOMPOS_TRACING || VmSettings.UNIFORM_TRACING) {
      TracingProcess result = new TracingProcess(obj, stopOnRoot, vm);
      if (VmSettings.KOMPOS_TRACING) {
        KomposTrace.activityCreation(ActivityType.PROCESS,
            result.getId(), result.getProcObject().getSOMClass().getName(), origin);
      } else if (VmSettings.UNIFORM_TRACING) {
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
    @Child RecordOneEvent                   traceProcCreation;

    @Override
    public final SpawnPrim initialize(final VM vm) {
      super.initialize(vm);
      onExec = insert(Breakpoints.create(sourceSection, BreakpointType.ACTIVITY_ON_EXEC, vm));
      notAValue = insert(ExceptionSignalingNode.createNotAValueNode(sourceSection));

      if (VmSettings.UNIFORM_TRACING) {
        traceProcCreation = insert(new RecordOneEvent(TraceRecord.ACTIVITY_CREATION));
      }

      forkJoinPool = vm.getForkJoinPool();
      processesPool = vm.getProcessPool();
      threadPool = vm.getThreadPool();

      return this;
    }

    @Specialization(guards = "clazz == TaskClass")
    @TruffleBoundary
    public final SomForkJoinTask spawnTask(final SClass clazz, final SBlock block) {
      SomForkJoinTask task = createTask(new Object[] {block},
          onExec.executeShouldHalt(), block, sourceSection, traceProcCreation, vm);
      forkJoinPool.execute(task);
      return task;
    }

    @Specialization(guards = "clazz == ThreadClass")
    @TruffleBoundary
    public final SomThreadTask spawnThread(final SClass clazz, final SBlock block) {
      SomThreadTask thread = createThread(new Object[] {block},
          onExec.executeShouldHalt(), block, sourceSection, traceProcCreation, vm);
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
          onExec.executeShouldHalt(), traceProcCreation, vm));
    }

    @Override
    protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
      if (tag == ActivityCreation.class ||
          tag == ExpressionBreakpoint.class ||
          tag == StatementTag.class) {
        return true;
      }
      return super.hasTagIgnoringEagerness(tag);
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

    @Child RecordOneEvent traceProcCreation;

    @Override
    public final SpawnWithPrim initialize(final VM vm) {
      super.initialize(vm);
      onExec = insert(Breakpoints.create(sourceSection, BreakpointType.ACTIVITY_ON_EXEC, vm));
      notAValue = insert(ExceptionSignalingNode.createNotAValueNode(sourceSection));

      if (VmSettings.UNIFORM_TRACING) {
        traceProcCreation = insert(new RecordOneEvent(TraceRecord.ACTIVITY_CREATION));
      }

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
          onExec.executeShouldHalt(), block, sourceSection, traceProcCreation, vm);
      forkJoinPool.execute(task);
      return task;
    }

    @Specialization(guards = "clazz == ThreadClass")
    @TruffleBoundary
    public SomThreadTask spawnThread(final SClass clazz, final SBlock block,
        final SArray somArgArr, final Object[] argArr) {
      SomThreadTask thread = createThread(argArr,
          onExec.executeShouldHalt(), block, sourceSection, traceProcCreation, vm);
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
          onExec.executeShouldHalt(), traceProcCreation, vm));
    }

    @Override
    protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
      if (tag == ActivityCreation.class ||
          tag == ExpressionBreakpoint.class ||
          tag == StatementTag.class) {
        return true;
      }
      return super.hasTagIgnoringEagerness(tag);
    }
  }
}
