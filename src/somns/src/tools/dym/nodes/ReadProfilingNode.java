package tools.dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import tools.dym.profiles.ReadValueProfile;


public final class ReadProfilingNode extends CountingNode<ReadValueProfile> {

  @Child protected TypeProfileNode typeProfile;

  public ReadProfilingNode(final ReadValueProfile profile) {
    super(profile);
    typeProfile = TypeProfileNodeGen.create(profile);
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    typeProfile.executeProfiling(result);
  }
}
