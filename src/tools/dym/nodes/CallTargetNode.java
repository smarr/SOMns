package tools.dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.interpreter.Invokable;
import tools.dym.profiles.CallsiteProfile;
import tools.dym.profiles.CallsiteProfile.Counter;


public class CallTargetNode extends CountingNode<CallsiteProfile> {

  protected final Counter cnt;

  public CallTargetNode(final CallsiteProfile profile, final Invokable invokable) {
    super(profile);
    cnt = profile.createCounter(invokable);
  }

  @Override
  public void onEnter(final VirtualFrame frame) {
    super.onEnter(frame);
    cnt.inc();
  }

  @Override
  public void onReturnValue(final VirtualFrame frame, final Object result) {}

  @Override
  public void onReturnExceptional(final VirtualFrame frame, final Throwable exception) {}
}
