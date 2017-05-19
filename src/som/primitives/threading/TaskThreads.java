package som.primitives.threading;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RecursiveTask;

import com.oracle.truffle.api.RootCallTarget;

import som.interpreter.SomLanguage;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.vm.Activity;
import som.vm.VmSettings;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.TracingActivityThread;
import tools.debugger.WebDebugger;
import tools.debugger.entities.ActivityType;

public final class TaskThreads {

  public abstract static class SomTaskOrThread extends RecursiveTask<Object> implements Activity {
    private static final long serialVersionUID = 4823503369882151811L;

    protected final Object[] argArray;
    protected final boolean stopOnRoot;

    public SomTaskOrThread(final Object[] argArray, final boolean stopOnRoot) {
      this.argArray   = argArray;
      this.stopOnRoot = stopOnRoot;
      assert argArray[0] instanceof SBlock : "First argument of a block needs to be the block object";
    }

    public final SInvokable getMethod() {
      return ((SBlock) argArray[0]).getMethod();
    }

    public boolean stopOnJoin() { return false; }

    @Override
    public int getNextTraceBufferId() {
      throw new UnsupportedOperationException("Should never be executed");
    }

    @Override
    protected final Object compute() {
      ObjectTransitionSafepoint.INSTANCE.register();
      try {
        RootCallTarget target = ((SBlock) argArray[0]).getMethod().getCallTarget();
        if (VmSettings.TRUFFLE_DEBUGGER_ENABLED && stopOnRoot) {
          WebDebugger dbg = SomLanguage.getVM(target.getRootNode()).getWebDebugger();
          dbg.prepareSteppingUntilNextRootNode();
        }
        if (VmSettings.ACTOR_TRACING) {
          ActorExecutionTrace.currentActivity(this);
        }

        ForkJoinThread thread = (ForkJoinThread) Thread.currentThread();
        thread.task = this;
        return target.call(argArray);
      } finally {
        ObjectTransitionSafepoint.INSTANCE.unregister();
      }
    }

    @Override
    public void setStepToNextTurn(final boolean val) {
      throw new UnsupportedOperationException(
          "Step to next turn is not supported " +
          "for threads. This code should never be reached.");
    }
  }

  public static class SomForkJoinTask extends SomTaskOrThread {
    private static final long serialVersionUID = -2145613708553535622L;

    public SomForkJoinTask(final Object[] argArray, final boolean stopOnRoot) {
      super(argArray, stopOnRoot);
    }

    @Override
    public String getName() {
      return getMethod().toString();
    }

    @Override
    public ActivityType getType() { return ActivityType.TASK; }
  }

  public static class TracedForkJoinTask extends SomForkJoinTask {
    private static final long serialVersionUID = -2763766745049695112L;

    private final long id;
    protected boolean stopOnJoin;

    private int nextTraceBufferId;

    public TracedForkJoinTask(final Object[] argArray, final boolean stopOnRoot) {
      super(argArray, stopOnRoot);
      this.id = TracingActivityThread.newEntityId();
    }

    @Override
    public final boolean stopOnJoin() {
      return stopOnJoin;
    }

    @Override
    public void setStepToJoin(final boolean val) { stopOnJoin = val; }

    @Override
    public int getNextTraceBufferId() {
      int result = nextTraceBufferId;
      nextTraceBufferId += 1;
      return result;
    }

    @Override
    public long getId() {
      return id;
    }
  }

  public static class SomThreadTask extends SomTaskOrThread {
    private static final long serialVersionUID = -8700297704150992350L;

    private String name;

    public SomThreadTask(final Object[] argArray, final boolean stopOnRoot) {
      super(argArray, stopOnRoot);
      name = "Thread(" + getMethod().getSignature().getString() + ")";
    }

    @Override
    public String getName() {
      return name;
    }

    public void setName(final String name) {
      this.name = name;
    }

    @Override
    public ActivityType getType() { return ActivityType.THREAD; }
  }

  public static class TracedThreadTask extends SomThreadTask {

    private static final long serialVersionUID = -7527703048413603761L;

    private final long id;
    protected boolean stopOnJoin;

    private int nextTraceBufferId;

    public TracedThreadTask(final Object[] argArray, final boolean stopOnRoot) {
      super(argArray, stopOnRoot);
      this.id = TracingActivityThread.newEntityId();
    }

    @Override
    public final boolean stopOnJoin() {
      return stopOnJoin;
    }

    @Override
    public void setStepToJoin(final boolean val) { stopOnJoin = val; }

    @Override
    public int getNextTraceBufferId() {
      int result = nextTraceBufferId;
      nextTraceBufferId += 1;
      return result;
    }

    @Override
    public long getId() {
      return id;
    }
  }

  public static final class ForkJoinThreadFactory implements ForkJoinWorkerThreadFactory {
    @Override
    public ForkJoinWorkerThread newThread(final ForkJoinPool pool) {
      return new ForkJoinThread(pool);
    }
  }

  private static final class ForkJoinThread extends TracingActivityThread {
    private SomTaskOrThread task;

    protected ForkJoinThread(final ForkJoinPool pool) {
      super(pool);
    }

    @Override
    public Activity getActivity() {
      return task;
    }
  }
}
