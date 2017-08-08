package som.primitives.arithmetic;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;


@GenerateNodeFactory
@Primitive(primitive = "doubleExp:", selector = "exp", receiverType = Double.class)
public abstract class ExpPrim extends UnaryExpressionNode {
  @Specialization
  public final double doExp(final double rcvr) {
    return Math.exp(rcvr);
  }
}
