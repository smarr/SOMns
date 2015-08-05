package som.primitives.arithmetic;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


@GenerateNodeFactory
@Primitive("doubleSin:")
public abstract class SinPrim extends UnaryExpressionNode {

  @Specialization
  public final double doSin(final double rcvr) {
    return Math.sin(rcvr);
  }
}
