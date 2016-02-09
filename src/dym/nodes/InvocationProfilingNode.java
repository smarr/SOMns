package dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;

import dym.DynamicMetrics;
import dym.profiles.InvocationProfile;


/**
 * Counts invocations and reports method enter/exit to track stack level.
 */
public class InvocationProfilingNode extends CountingNode<InvocationProfile> {

  private final DynamicMetrics meter;

  public InvocationProfilingNode(final DynamicMetrics meter,
      final InvocationProfile counter) {
    super(counter);
    this.meter = meter;
  }

  @Override
  protected void onEnter(final VirtualFrame frame) {
    super.onEnter(frame);
    counter.profileArguments(frame.getArguments());
    meter.enterMethod();
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    meter.leaveMethod();
  }

  @Override
  protected void onReturnExceptional(final VirtualFrame frame, final Throwable e) {
    meter.leaveMethod();
  }

  @Override
  public NodeCost getCost() {
    return NodeCost.NONE;
  }
}
