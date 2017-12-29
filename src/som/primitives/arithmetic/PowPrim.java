package som.primitives.arithmetic;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.interpreter.nodes.nary.BinaryExpressionNode;


@GenerateNodeFactory
@Primitive(primitive = "doublePow:exp:", selector = "pow:", receiverType = Double.class)
public abstract class PowPrim extends BinaryExpressionNode {

  @Specialization
  public final double doPow(final double base, final double exponent) {
    return Math.pow(base, exponent);
  }
}
