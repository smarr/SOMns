package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class DoublePrims  {

  public abstract static class RoundPrim extends UnarySideEffectFreeExpressionNode {
    public RoundPrim(final boolean executesEnforced) { super(executesEnforced); }
    public RoundPrim(final RoundPrim node) { this(node.executesEnforced); }

    @Specialization
    public final long doDouble(final double receiver) {
      return Math.round(receiver);
    }
  }
}
