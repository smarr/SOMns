package som.primitives.arithmetic;

import java.math.BigInteger;

import som.interpreter.nodes.nary.BinaryExpressionNode;


public abstract class ArithmeticPrim extends BinaryExpressionNode {

  protected final Number intOrBigInt(final long val) {
    if (val > Integer.MAX_VALUE || val < Integer.MIN_VALUE) {
      return BigInteger.valueOf(val);
    } else {
      return (int) val;
    }
  }

  protected final Number reduceToIntIfPossible(final BigInteger result) {
    if (result.bitLength() > 31) {
      return result;
    } else {
      return result.intValue();
    }
  }
}
