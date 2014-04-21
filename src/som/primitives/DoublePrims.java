package som.primitives;

import java.math.BigInteger;

import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class DoublePrims  {

  public abstract static class RoundPrim extends UnarySideEffectFreeExpressionNode {
    @Specialization
    public final Object doDouble(final double receiver) {
      long val = Math.round(receiver);
      if (val > Integer.MAX_VALUE || val < Integer.MIN_VALUE) {
        return BigInteger.valueOf(val);
      } else {
        return (int) val;
      }
    }
  }
}
