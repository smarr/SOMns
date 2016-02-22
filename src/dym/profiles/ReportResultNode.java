package dym.profiles;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;


public class ReportResultNode extends ExecutionEventNode {
  protected final PrimitiveOperationProfile profile;
  protected final int argIdx;

  public ReportResultNode(final PrimitiveOperationProfile profile, final int argIdx) {
    this.profile = profile;
    this.argIdx  = argIdx;
  }

  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    profile.profileArgument(argIdx, result);
  }

  protected void onReturnExceptional(final VirtualFrame frame, final Throwable exception) {
    profile.profileArgument(argIdx, exception);
  }
}
