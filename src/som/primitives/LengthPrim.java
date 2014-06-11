package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class LengthPrim extends UnarySideEffectFreeExpressionNode {
  public LengthPrim() { super(false); } /* TODO: enforced!!! */

  @Specialization
  public final long doSArray(final Object[] receiver) {
    return receiver.length;
  }

  @Specialization
  public final long doSString(final String receiver) {
    return receiver.length();
  }
}
