package som.primitives.arithmetic;

import java.math.BigInteger;

import som.interpreter.nodes.nary.UnaryExpressionNode;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class SqrtPrim extends UnaryExpressionNode {

  @Specialization
  public final Number doLong(final long receiver) {
    double result = Math.sqrt(receiver);

    if (result == Math.rint(result)) {
      return (long) result;
    } else {
      return result;
    }
  }

  @Specialization
  public final double doBigInteger(final BigInteger receiver) {
    return Math.sqrt(receiver.doubleValue());
  }

  @Specialization
  public final double doDouble(final double receiver) {
    return Math.sqrt(receiver);
  }
}
