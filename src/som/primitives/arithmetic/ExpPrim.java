package som.primitives.arithmetic;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.interpreter.nodes.nary.UnaryExpressionNode;


@GenerateNodeFactory
@Primitive(primitive = "doubleExp:", selector = "exp", receiverType = Double.class)
public abstract class ExpPrim extends UnaryExpressionNode {
  @Specialization
  public final double doExp(final double rcvr) {
    return Math.exp(rcvr);
  }
}
