package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.primitives.threading.TaskThreads.SomForkJoinTask;
import som.vm.VmSettings;
import tools.concurrency.TracingActivityThread;
import tools.concurrency.WorkStealingWorker;


public class OptJoinNode extends ExprWithTagsNode {

  @Child private ExpressionNode    receiver;
  @Child private BlockDispatchNode dispatch;

  public OptJoinNode(final ExpressionNode receiver) {
    this.receiver = receiver;
    this.dispatch = BlockDispatchNodeGen.create();
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {

    SomForkJoinTask task = (SomForkJoinTask) receiver.executeGeneric(frame);
    TracingActivityThread currentThread = TracingActivityThread.currentThread();

    while (task.result == null) {
      boolean stolenTask = WorkStealingWorker.tryStealingAndExecuting(currentThread, dispatch);
      if (VmSettings.ENABLE_BACKOFF) {
        WorkStealingWorker.doBackoffIfNecessary(currentThread, stolenTask);
      }
    }
    return task.result;
  }
}
