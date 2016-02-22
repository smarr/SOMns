package dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;

import dym.profiles.OperationProfile;


public class ReportResultNode extends ExecutionEventNode {
  protected final OperationProfile profile;
  protected final int argIdx;

  public ReportResultNode(final OperationProfile profile, final int argIdx) {
    this.profile = profile;
    this.argIdx  = argIdx;
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    profile.profileArgument(argIdx, result);
  }

  @Override
  protected void onReturnExceptional(final VirtualFrame frame, final Throwable exception) {
    profile.profileArgument(argIdx, exception);
  }
}
