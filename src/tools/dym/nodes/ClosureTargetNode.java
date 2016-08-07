package tools.dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.interpreter.Invokable;
import tools.dym.profiles.ClosureApplicationProfile;
import tools.dym.profiles.ClosureApplicationProfile.ActivationCounter;


public final class ClosureTargetNode extends CountingNode<ClosureApplicationProfile> {

  protected final ActivationCounter cnt;

  public ClosureTargetNode(final ClosureApplicationProfile profile, final Invokable invokable) {
    super(profile);
    cnt = profile.createCounter(invokable);
  }

  @Override
  public void onEnter(final VirtualFrame frame) {
    super.onEnter(frame);
    cnt.inc();
  }

  @Override
  public void onReturnValue(final VirtualFrame frame, final Object result) { }

  @Override
  public void onReturnExceptional(final VirtualFrame frame, final Throwable exception) { }
}
