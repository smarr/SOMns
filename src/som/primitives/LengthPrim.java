package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vmobjects.SArray;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class LengthPrim extends UnarySideEffectFreeExpressionNode {
  @Specialization
  public final int doSArray(final SArray receiver) {
    return receiver.getNumberOfIndexableFields();
  }

  @Specialization
  public final int doSString(final String receiver) {
    return receiver.length();
  }
}
