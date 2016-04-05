package dym.nodes;

import som.interpreter.Invokable;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;

import dym.profiles.CallsiteProfile;


public class CallTargetNode extends ExecutionEventNode {

  protected final CallsiteProfile profile;
  protected final Invokable invokable; // TODO: should I try harder to get SInvokable?

  public CallTargetNode(final CallsiteProfile profile, final Invokable invokable) {
    this.profile = profile;
    this.invokable = invokable;
  }

  @Override
  public void onEnter(final VirtualFrame frame) {
    profile.recordInvocationTarget(invokable);
  }

  @Override
  public void onReturnValue(final VirtualFrame frame, final Object result) {

  }

  @Override
  public void onReturnExceptional(final VirtualFrame frame, final Throwable exception) {

  }
}
