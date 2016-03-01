package dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;


/**
 * This node will try to specialize itself at a later point, hopefully
 * the other relevant nodes are already available then.
 */
public class LateReportResultNode extends ExecutionEventNode {
  private final EventContext ctx;
  private final ExecutionEventNodeFactory factory;

  public LateReportResultNode(final EventContext ctx, final ExecutionEventNodeFactory factory) {
    this.ctx     = ctx;
    this.factory = factory;
  }

  private ExecutionEventNode specialize() {
    ExecutionEventNode parent = ctx.findDirectParentEventNode(factory);

    if (parent == null) {
      return this;
    }

    @SuppressWarnings("unchecked")
    OperationProfilingNode p = (OperationProfilingNode) parent;
    int idx = p.registerSubexpressionAndGetIdx(ctx.getInstrumentedNode());
    return replace(new ReportResultNode(p.getProfile(), idx));
  }

  @Override
  protected void onEnter(final VirtualFrame frame) {
    ExecutionEventNode node = specialize();
    if (node == this) {
      return;
    } else {
      ((ReportResultNode) node).onEnter(frame);
    }
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    ExecutionEventNode node = specialize();
    if (node == this) {
      return;
    } else {
      ((ReportResultNode) node).onReturnValue(frame, result);
    }
  }

  @Override
  protected void onReturnExceptional(final VirtualFrame frame, final Throwable exception) {
    ExecutionEventNode node = specialize();
    if (node == this) {
      return;
    } else {
      ((ReportResultNode) node).onReturnExceptional(frame, exception);
    }
  }
}
