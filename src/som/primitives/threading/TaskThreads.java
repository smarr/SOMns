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
import tools.concurrency.TracingActivityThread;
import tools.debugger.WebDebugger;
import tools.debugger.entities.ActivityType;

public final class TaskThreads {

  public static class SomForkJoinTask extends RecursiveTask<Object> implements Activity {
    private static final long serialVersionUID = -2145613708553535622L;

    private final Object[] argArray;
    private final boolean stopOnRoot;
    private boolean stopOnJoin;

    public SomForkJoinTask(final Object[] argArray, final boolean stopOnRoot) {
      this.argArray   = argArray;
      this.stopOnRoot = stopOnRoot;
      assert argArray[0] instanceof SBlock : "First argument of a block needs to be the block object";
    }

    @Override
    public ActivityType getType() { return ActivityType.TASK; }

    public final SInvokable getMethod() {
      return ((SBlock) argArray[0]).getMethod();
    }

    public boolean stopOnJoin() {
      return stopOnJoin;
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
    public void setStepToJoin(final boolean val) { stopOnJoin = val; }

    @Override
    protected final Object compute() {
      ObjectTransitionSafepoint.INSTANCE.register();
      try {
        RootCallTarget target = ((SBlock) argArray[0]).getMethod().getCallTarget();
        if (VmSettings.TRUFFLE_DEBUGGER_ENABLED && stopOnRoot) {
          WebDebugger dbg = SomLanguage.getVM(target.getRootNode()).getWebDebugger();
          dbg.prepareSteppingUntilNextRootNode();
          ForkJoinThread thread = (ForkJoinThread) Thread.currentThread();
          thread.task = this;
        }
        return target.call(argArray);
      } finally {
        ObjectTransitionSafepoint.INSTANCE.unregister();
      }
    }
  }

  public static class TracedForkJoinTask extends SomForkJoinTask {
    private static final long serialVersionUID = -2763766745049695112L;

    private final long id;

    public TracedForkJoinTask(final Object[] argArray, final boolean stopOnRoot) {
      super(argArray, stopOnRoot);
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
    private SomForkJoinTask task;

    protected ForkJoinThread(final ForkJoinPool pool) {
      super(pool);
    }

    @Override
    public long getCurrentMessageId() {
      return 0;
    }

    @Override
    public Activity getActivity() {
      return task;
    }
  }
}
