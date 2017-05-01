package som.primitives.threading;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RecursiveTask;

import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.vm.Activity;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import tools.concurrency.TracingActivityThread;
import tools.debugger.entities.ActivityType;

public final class TaskThreads {

  public static class SomForkJoinTask extends RecursiveTask<Object> implements Activity {
    private static final long serialVersionUID = -2145613708553535622L;

    private final Object[] argArray;

    public SomForkJoinTask(final Object[] argArray) {
      this.argArray = argArray;
      assert argArray[0] instanceof SBlock : "First argument of a block needs to be the block object";
    }

    @Override
    public ActivityType getType() { return ActivityType.TASK; }

    public final SInvokable getMethod() {
      return ((SBlock) argArray[0]).getMethod();
    }

    @Override
    public final String getName() {
      return getMethod().toString();
    }

    @Override
    public long getId() {
      return 0;
    }

    @Override
    protected final Object compute() {
      ObjectTransitionSafepoint.INSTANCE.register();
      try {
        return ((SBlock) argArray[0]).getMethod().getCallTarget().call(argArray);
      } finally {
        ObjectTransitionSafepoint.INSTANCE.unregister();
      }
    }
  }

  public static class TracedForkJoinTask extends SomForkJoinTask {
    private static final long serialVersionUID = -2763766745049695112L;

    private final long id;

    public TracedForkJoinTask(final Object[] argArray) {
      super(argArray);
      if (Thread.currentThread() instanceof TracingActivityThread) {
        TracingActivityThread t = (TracingActivityThread) Thread.currentThread();
        this.id = t.generateActivityId();
      } else {
        this.id = 0; // main actor
      }
    }

    @Override
    public long getId() {
      return id;
    }
  }

  public static final class ForkJoinThreadFactor implements ForkJoinWorkerThreadFactory {
    @Override
    public ForkJoinWorkerThread newThread(final ForkJoinPool pool) {
      return new ForkJoinThread(pool);
    }
  }

  private static final class ForkJoinThread extends TracingActivityThread {
    protected ForkJoinThread(final ForkJoinPool pool) {
      super(pool);
    }

    @Override
    public long getCurrentMessageId() {
      return 0;
    }

    @Override
    public Activity getActivity() {
      return null;
    }
  }
}
