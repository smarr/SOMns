package dym.nodes;

import som.interpreter.Types;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;

import dym.profiles.CallsiteProfile;


public class ReportReceiverNode extends ExecutionEventNode {
  private final CallsiteProfile profile;

  public ReportReceiverNode(final CallsiteProfile profile) {
    this.profile = profile;
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    // TODO: make language independent
    profile.recordReceiverType(Types.getClassOf(result));
  }

  @Override
  protected void onReturnExceptional(final VirtualFrame frame, final Throwable exception) {
    // TODO: make language independent
    // TODO: properly handle exception types, if necessary
    profile.recordReceiverType(Types.getClassOf(exception));
  }
}
