package som.primitives.arithmetic;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


@GenerateNodeFactory
@Primitive("doubleExp:")
public abstract class ExpPrim extends UnaryExpressionNode {

  @Specialization
  public final double doExp(final double rcvr) {
    return Math.exp(rcvr);
  }
}
