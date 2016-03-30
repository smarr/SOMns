package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;


public abstract class DoublePrims  {

  @GenerateNodeFactory
  @Primitive("doubleRound:")
  public abstract static class RoundPrim extends UnaryExpressionNode {
    public RoundPrim(final SourceSection source) { super(source); }

    @Specialization
    public final long doDouble(final double receiver) {
      return Math.round(receiver);
    }
  }

  @GenerateNodeFactory
  @Primitive("doublePositiveInfinity:")
  public abstract static class PositiveInfinityPrim extends UnaryExpressionNode {
    public PositiveInfinityPrim(final SourceSection source) { super(source); }

    @Specialization
    public final double doSClass(final Object receiver) {
      return Double.POSITIVE_INFINITY;
    }
  }
}
