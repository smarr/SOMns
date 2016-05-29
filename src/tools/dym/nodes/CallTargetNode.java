package tools.dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.interpreter.Invokable;
import tools.dym.profiles.CallsiteProfile;


public class CallTargetNode extends CountingNode<CallsiteProfile> {

  protected final Invokable invokable; // TODO: should I try harder to get SInvokable?

  public CallTargetNode(final CallsiteProfile profile, final Invokable invokable) {
    super(profile);
    this.invokable = invokable;
  }

  @Override
  public void onEnter(final VirtualFrame frame) {
    super.onEnter(frame);
    counter.recordInvocationTarget(invokable);
  }

  @Override
  public void onReturnValue(final VirtualFrame frame, final Object result) { }

  @Override
  public void onReturnExceptional(final VirtualFrame frame, final Throwable exception) { }
}
