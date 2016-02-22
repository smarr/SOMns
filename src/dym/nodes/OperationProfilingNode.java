package dym.nodes;

import som.interpreter.ReturnException;
import som.vm.NotYetImplementedException;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.nodes.Node;

import dym.profiles.OperationProfile;


/**
 * This is for primitive operations only, this mean they cannot cause recursion.
 */
public final class OperationProfilingNode<T extends OperationProfile> extends CountingNode<T> {

  private final EventContext context;

  public OperationProfilingNode(
      final T profile, final EventContext context) {
    super(profile);
    this.context = context;
  }

  public T getProfile() {
    return counter;
  }

  @Override
  protected void onEnter(final VirtualFrame frame) {
    counter.enterMainNode();
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    counter.profileReturn(result);
  }

  @Override
  protected void onReturnExceptional(final VirtualFrame frame, final Throwable e) {
    // TODO: make language independent
    if (e instanceof ReturnException) {
      counter.profileReturn(((ReturnException) e).result());
    } else {
      throw new NotYetImplementedException();
    }
  }

  public int registerSubexpressionAndGetIdx(final Node subExpr) {
    assert isSubexpressionOfInstrumentedNode(subExpr) : "subExpr does not seem to be a subexrpression of this node, a bug, a data race?";
    return counter.registerSubexpressionAndGetIdx();
  }

  private boolean isSubexpressionOfInstrumentedNode(final Node subExpr) {
    for (Node n : context.getInstrumentedNode().getChildren()) {
      if (n == subExpr) {
        return true;
      }
      if (n instanceof WrapperNode && ((WrapperNode) n).getDelegateNode() == subExpr) {
        return true;
      }
    }
    return false;
  }
}
