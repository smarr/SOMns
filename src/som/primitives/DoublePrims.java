package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


public abstract class DoublePrims  {

  @GenerateNodeFactory
  @Primitive("doubleRound:")
  public abstract static class RoundPrim extends UnaryExpressionNode {
    @Specialization
    public final long doDouble(final double receiver) {
      return Math.round(receiver);
    }
  }

  @GenerateNodeFactory
  @Primitive("doublePositiveInfinity:")
  public abstract static class PositiveInfinityPrim extends UnaryExpressionNode {
    @Specialization
    public final double doSClass(final Object receiver) {
      return Double.POSITIVE_INFINITY;
    }
  }
}
