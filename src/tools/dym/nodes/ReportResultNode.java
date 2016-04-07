package tools.dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;

import tools.dym.profiles.OperationProfile;


public class ReportResultNode extends ExecutionEventNode {
  protected final OperationProfile profile;
  protected final int argIdx;

  public ReportResultNode(final OperationProfile profile, final int argIdx) {
    this.profile = profile;
    this.argIdx  = argIdx;
  }

  @Override
  public void onEnter(final VirtualFrame frame) {  }

  @Override
  public void onReturnValue(final VirtualFrame frame, final Object result) {
    profile.profileArgument(argIdx, result);
  }

  @Override
  public void onReturnExceptional(final VirtualFrame frame, final Throwable exception) {
    profile.profileArgument(argIdx, exception);
  }
}
