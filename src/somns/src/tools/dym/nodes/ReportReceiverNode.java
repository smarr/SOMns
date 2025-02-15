package tools.dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;

import tools.dym.profiles.CallsiteProfile;


public class ReportReceiverNode extends ExecutionEventNode {
  @Child protected TypeProfileNode typeProfile;

  public ReportReceiverNode(final CallsiteProfile profile) {
    typeProfile = TypeProfileNodeGen.create(profile);
    profile.setReceiverProfile(typeProfile);
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    typeProfile.executeProfiling(result);
  }

  @Override
  protected void onReturnExceptional(final VirtualFrame frame, final Throwable exception) {
    typeProfile.executeProfiling(exception);
  }
}
