package som.primitives.arithmetic;

import java.math.BigInteger;

import som.interpreter.nodes.nary.UnaryExpressionNode;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class SqrtPrim extends UnaryExpressionNode {
  private Number intOrBigInt(final long val) {
    if (val > Integer.MAX_VALUE || val < Integer.MIN_VALUE) {
      return BigInteger.valueOf(val);
    } else {
      return (int) val;
    }
  }

  @Specialization
  public final Object doInteger(final int receiver) {
    double result = Math.sqrt(receiver);

    if (result == Math.rint(result)) {
      return intOrBigInt((long) result);
    } else {
      return result;
    }
  }

  @Specialization
  public final double doBigInteger(final BigInteger receiver) {
    return Math.sqrt(receiver.doubleValue());
  }

  @Specialization
  public final double doDouble(final double receiver) {
    return Math.sqrt(receiver);
  }
}
