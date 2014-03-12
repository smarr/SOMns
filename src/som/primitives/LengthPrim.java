package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SArray;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class LengthPrim extends UnaryExpressionNode {
  @Specialization
  public int doSArray(final SArray receiver) {
    return receiver.getNumberOfIndexableFields();
  }

  @Specialization
  public int doSString(final String receiver) {
    return receiver.length();
  }

  @Override
  public final void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
}
