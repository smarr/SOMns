package tools.concurrency;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.VM;
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.primitives.threading.TaskThreads.SomForkJoinTask;
import som.vm.VmSettings;
import som.vmobjects.SBlock;


public class WorkStealingWorker implements Runnable {

  @Override
  public void run() {
    TracingActivityThread currentThread = TracingActivityThread.currentThread();

    while (VM.numWSThreads > 0) {
      boolean stolenTask = tryStealingAndExecuting(currentThread);
      if (VmSettings.ENABLE_BACKOFF) {
        doBackoffIfNecessary(currentThread, stolenTask);
      }
    }
  }

  public static void doBackoffIfNecessary(final TracingActivityThread currentThread,
      final boolean stolenTask) {
    if (stolenTask) {
      currentThread.workStealingTries = 0;
    } else {
      WorkStealingWorker.backOffBeforeRetryingStealing(currentThread);
    }
  }

  public static boolean tryStealingAndExecuting(final TracingActivityThread currentThread) {
    SomForkJoinTask sf = stealTask(currentThread);

    if (sf == null) {
      TracingActivityThread victim = selectVictim(currentThread);

      if (victim == null) {
        return false;
      }

      if (victim != currentThread) {
        sf = stealFromOther(victim);
      }
    }

    if (sf != null && !sf.stolen) {
      assert sf.result == null;

      sf.stolen = true;

      SBlock block = (SBlock) sf.evaluateArgsForSpawn[0];
      Object result = block.getMethod().invoke(sf.evaluateArgsForSpawn);

      assert sf.result == null;
      sf.result = result;
      return true;
    }

    return false;
  }

  public static boolean tryStealingAndExecuting(final TracingActivityThread currentThread,
      final BlockDispatchNode dispatch) {

    SomForkJoinTask sf = stealTask(currentThread);

    if (sf == null) {
      TracingActivityThread victim = selectVictim(currentThread);

      if (victim == null) {
        return false;
      }

      if (victim != currentThread) {
        sf = stealFromOther(victim);
      }
    }

    if (sf != null && !sf.stolen) {
      assert sf.result == null;

      sf.stolen = true;

      Object result = dispatch.executeDispatch(sf.evaluateArgsForSpawn);
      assert sf.result == null;
      sf.result = result;
      return true;
    }

    return false;
  }

  private static TracingActivityThread selectVictim(
      final TracingActivityThread currentThread) {
    int numThreads = VM.numWSThreads;
    if (numThreads == 0) {
      return null;
    }

    int victimIdx = currentThread.backoffRnd.next(numThreads);

    return VM.threads[victimIdx];
  }

  @TruffleBoundary
  private static SomForkJoinTask stealTask(final TracingActivityThread victim) {
    if (VmSettings.ENABLE_PARALLEL) {
      return victim.taskQueue.pollLast();
    } else {
      return victim.taskQueue.poll();
    }
  }

  @TruffleBoundary
  private static SomForkJoinTask stealFromOther(final TracingActivityThread victim) {
    return victim.taskQueue.poll();
  }

  private static final int maxWaitTime          = 250;
  private static final int reTriesBeforeWaiting = 50000;

  @TruffleBoundary
  public static void backOffBeforeRetryingStealing(final TracingActivityThread thread) {
    thread.workStealingTries += 1;

    if (thread.workStealingTries < reTriesBeforeWaiting) {
      return;
    }

    long waitTime = Math.min(maxWaitTime,
        getWaitTime(thread.workStealingTries - reTriesBeforeWaiting, thread));

    try {
      Thread.sleep(waitTime);
    } catch (InterruptedException e) {}
  }

  private static long getWaitTime(final int retryCount, final TracingActivityThread thread) {
    return retryCount * thread.backoffRnd.next(maxWaitTime / 20);
  }
}
