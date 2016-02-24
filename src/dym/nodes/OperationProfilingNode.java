package dym.nodes;

import som.compiler.Tags;
import som.interpreter.ReturnException;
import som.vm.NotYetImplementedException;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.nodes.Node;

import dym.Tagging.Tagged;
import dym.profiles.OperationProfile;


/**
 * This is for primitive operations only, this mean they cannot cause recursion.
 */
public final class OperationProfilingNode extends CountingNode<OperationProfile> {

  private final EventContext context;

  public OperationProfilingNode(
      final OperationProfile profile, final EventContext context) {
    super(profile);
    this.context = context;
  }

  public OperationProfile getProfile() {
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
    int idx = getChildIdx(subExpr);
    assert idx >= 0 : "Subexpression was not found. Something seems to be wrong with the instrumentation.";
    return idx + 1; // + 1 is used to represent the index of the storage array used to hold the result. Return value is at 0 index.
  }

  private int getChildIdx(final Node subExpr) {
    int taggedIdx = 0;
    for (Node n : context.getInstrumentedNode().getChildren()) {
      if (n == subExpr) {
        assert n instanceof Tagged;
        assert ((Tagged) n).getSourceSection().hasTag(Tags.PRIMITIVE_ARGUMENT);
        return taggedIdx;
      }
      if (n instanceof WrapperNode && ((WrapperNode) n).getDelegateNode() == subExpr) {
        assert n instanceof Tagged;
        assert ((Tagged) n).getSourceSection().hasTag(Tags.PRIMITIVE_ARGUMENT);
        return taggedIdx;
      }

      if (n instanceof Tagged && ((Tagged) n).getSourceSection().hasTag(Tags.PRIMITIVE_ARGUMENT)) {
        taggedIdx += 1;
      }
    }
    return -1;
  }
}
