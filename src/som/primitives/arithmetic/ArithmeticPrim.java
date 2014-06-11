package som.primitives.arithmetic;

import java.math.BigInteger;

import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;


public abstract class ArithmeticPrim extends BinarySideEffectFreeExpressionNode {
  public ArithmeticPrim() { super(false); } /* TODO: enforced!!! */
  protected final Number reduceToIntIfPossible(final BigInteger result) {
    if (result.bitLength() > 31) {
      return result;
    } else {
      return result.intValue();
    }
  }
}
