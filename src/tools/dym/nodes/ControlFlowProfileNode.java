package tools.dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import tools.dym.profiles.BranchProfile;


public class ControlFlowProfileNode extends CountingNode<BranchProfile> {

  public ControlFlowProfileNode(final BranchProfile profile) {
    super(profile);
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    counter.profile((boolean) result);
  }
}
