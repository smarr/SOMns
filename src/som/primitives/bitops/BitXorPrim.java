package som.primitives.bitops;

import som.interpreter.nodes.nary.BinaryExpressionNode;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


@GenerateNodeFactory
public abstract class BitXorPrim extends BinaryExpressionNode {
  @Specialization
  public final long doLong(final long receiver, final long right) {
    return receiver ^ right;
  }
}
