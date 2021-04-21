package tools.dym.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;

import som.interpreter.ReturnException;
import som.vm.NotYetImplementedException;
import tools.dym.profiles.OperationProfile;


/**
 * This is for primitive operations only, this mean they cannot cause recursion.
 */
public final class OperationProfilingNode extends CountingNode<OperationProfile> {

  public OperationProfilingNode(final OperationProfile profile) {
    super(profile);
  }

  @Override
  public OperationProfile getProfile() {
    return counter;
  }

  @Override
  protected void onEnter(final VirtualFrame frame) {
    super.onEnter(frame);
    counter.enterMainNode();
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    counter.profileReturn(result);
  }

  @Override
  protected void onInputValue(final VirtualFrame frame, final EventContext inputContext,
      final int inputIndex, final Object inputValue) {
    counter.profileArgument(inputIndex, inputValue);
  }

  @Override
  protected void onReturnExceptional(final VirtualFrame frame, final Throwable e) {
    // TODO: make language independent
    if (e instanceof ReturnException) {
      counter.profileReturn(((ReturnException) e).result());
    } else {
      CompilerDirectives.transferToInterpreter();
      throw new NotYetImplementedException();
    }
  }
}
