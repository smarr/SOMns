package dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;

import dym.profiles.LoopProfile;


public class LoopIterationReportNode extends ExecutionEventNode {
  protected final LoopProfile profile;

  public LoopIterationReportNode(final LoopProfile profile) {
    this.profile = profile;
  }

  @Override
  protected void onEnter(final VirtualFrame frame) {
    profile.recordLoopIteration();
  }
}
