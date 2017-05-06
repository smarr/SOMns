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

  public abstract static class SomTaskOrThread extends RecursiveTask<Object> implements Activity {
    private static final long serialVersionUID = 4823503369882151811L;

    protected final Object[] argArray;
    protected final boolean stopOnRoot;
    protected boolean stopOnJoin;

    public SomTaskOrThread(final Object[] argArray, final boolean stopOnRoot) {
      this.argArray   = argArray;
      this.stopOnRoot = stopOnRoot;
      assert argArray[0] instanceof SBlock : "First argument of a block needs to be the block object";
    }

    public final SInvokable getMethod() {
      return ((SBlock) argArray[0]).getMethod();
    }

    public final boolean stopOnJoin() {
      return stopOnJoin;
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
        }

        ForkJoinThread thread = (ForkJoinThread) Thread.currentThread();
        thread.task = this;
        return target.call(argArray);
      } finally {
        ObjectTransitionSafepoint.INSTANCE.unregister();
      }
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

    public TracedForkJoinTask(final Object[] argArray, final boolean stopOnRoot) {
      super(argArray, stopOnRoot);
      if (Thread.currentThread() instanceof TracingActivityThread) {
        TracingActivityThread t = TracingActivityThread.currentThread();
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

    public TracedThreadTask(final Object[] argArray, final boolean stopOnRoot) {
      super(argArray, stopOnRoot);
      if (Thread.currentThread() instanceof TracingActivityThread) {
        TracingActivityThread t = TracingActivityThread.currentThread();
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
    public long getCurrentMessageId() {
      return -1;
    }

    @Override
    public Activity getActivity() {
      return task;
    }
  }
}
