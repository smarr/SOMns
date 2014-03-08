package som.primitives;

import java.math.BigInteger;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.arithmetic.ArithmeticPrim;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class DoublePrims  {

  public abstract static class RoundPrim extends UnaryExpressionNode {
    @Specialization
    public Object doDouble(final double receiver) {
      long val = Math.round(receiver);
      if (val > Integer.MAX_VALUE || val < Integer.MIN_VALUE) {
        return BigInteger.valueOf(val);
      } else {
        return (int) val;
      }
    }
  }

  public abstract static class BitXorPrim extends ArithmeticPrim {
    @Specialization
    public double doDouble(final double receiver, final double right) {
      long left = (long) receiver;
      long rightLong = (long) right;
      return left ^ rightLong;
    }

    @Specialization
    public double doDouble(final double receiver, final int right) {
      long left = (long) receiver;
      long rightLong = right;
      return left ^ rightLong;
    }
  }
}
