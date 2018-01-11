package som.interpreter.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;

import bd.nodes.PreevaluatedExpression;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.primitives.threading.TaskThreads.SomForkJoinTask;
import som.vm.VmSettings;
import som.vmobjects.SBlock;
import tools.concurrency.TracingActivityThread;


public final class OptTaskNode extends ExprWithTagsNode {

  @Child private ExpressionNode valueSend;
  @Child private ExpressionNode block;

  @Children private final ExpressionNode[] argArray;

  private final ConditionProfile condProf = ConditionProfile.createCountingProfile();

  public OptTaskNode(final ExpressionNode valueSend, final ExpressionNode block,
      final ExpressionNode[] argArray) {
    this.valueSend = valueSend;
    this.block = block;
    this.argArray = argArray;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {

    SomForkJoinTask somTask;

    Object[] args = executeArgs(frame);

    assert args[0] instanceof SBlock;

    TracingActivityThread tracingThread = TracingActivityThread.currentThread();

    if (VmSettings.ENABLE_PARALLEL) {
      somTask = new SomForkJoinTask(args);
      offerTaskForStealing(somTask, tracingThread);
    } else {
      if (condProf.profile(isSystemLikelyIdle(tracingThread))) {
        somTask = new SomForkJoinTask(args);
        offerTaskForStealing(somTask, tracingThread);
      } else {
        somTask = new SomForkJoinTask(null);
        somTask.result = ((PreevaluatedExpression) valueSend).doPreEvaluated(frame, args);
      }
    }

    return somTask;
  }

  @ExplodeLoop
  private Object[] executeArgs(final VirtualFrame frame) {
    Object[] executedArgArray = new Object[argArray.length + 1];
    int i = 1;

    executedArgArray[0] = block.executeGeneric(frame);

    for (ExpressionNode e : argArray) {
      executedArgArray[i] = e.executeGeneric(frame);
      i++;
    }
    return executedArgArray;
  }

  @TruffleBoundary
  private void offerTaskForStealing(final SomForkJoinTask somTask,
      final TracingActivityThread tracingThread) {
    try {
      tracingThread.taskQueue.put(somTask);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @TruffleBoundary
  public static boolean isSystemLikelyIdle(
      final TracingActivityThread tracingThread) {
    return tracingThread.taskQueue.isEmpty();
  }
}
