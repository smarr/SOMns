package tools.dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import tools.dym.profiles.AllocationProfile;
import tools.dym.profiles.AllocationProfile.AllocProfileNode;


public final class AllocationProfilingNode extends CountingNode<AllocationProfile> {
  @Child private AllocProfileNode profile;

  public AllocationProfilingNode(final AllocationProfile profile) {
    super(profile);
    this.profile = profile.getProfile();
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    profile.executeProfiling(result);
  }
}
