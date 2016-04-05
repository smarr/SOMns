package dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import dym.profiles.LoopProfile;


public class LoopProfilingNode extends CountingNode<LoopProfile> {

  public LoopProfilingNode(final LoopProfile profile) {
    super(profile);
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    counter.recordLoopExit();
  }

  public LoopProfile getProfile() {
    return counter;
  }
}
