package som.primitives.arithmetic;

import java.math.BigInteger;

import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;


public abstract class ArithmeticPrim extends BinarySideEffectFreeExpressionNode {
  protected final Number reduceToLongIfPossible(final BigInteger result) {
    if (result.bitLength() > Long.SIZE - 1) {
      return result;
    } else {
      return result.longValue();
    }
  }
}
