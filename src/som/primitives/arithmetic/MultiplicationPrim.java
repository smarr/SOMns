package som.primitives.arithmetic;

import java.math.BigInteger;

import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.dsl.Specialization;


public abstract class MultiplicationPrim extends ArithmeticPrim {
  public MultiplicationPrim(final boolean executesEnforced) { super(executesEnforced); }
  public MultiplicationPrim(final MultiplicationPrim node) { this(node.executesEnforced); }

  @Specialization(order = 1, rewriteOn = ArithmeticException.class)
  public final long doLong(final long left, final long right) {
    return ExactMath.multiplyExact(left, right);
  }

  @Specialization(order = 2)
  public final Object doLongWithOverflow(final long left, final long right) {
    return BigInteger.valueOf(left).multiply(BigInteger.valueOf(right));
  }

  @Specialization(order = 5)
  public final Object doBigInteger(final BigInteger left, final BigInteger right) {
    BigInteger result = left.multiply(right);
    return reduceToIntIfPossible(result);
  }

  @Specialization(order = 6)
  public final double doDouble(final double left, final double right) {
    return left * right;
  }

  @Specialization(order = 10)
  public final Object doLong(final long left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 11)
  public final double doLong(final long left, final double right) {
    return doDouble(left, right);
  }

  @Specialization(order = 12)
  public final Object doBigInteger(final BigInteger left, final long right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 13)
  public final double doDouble(final double left, final long right) {
    return doDouble(left, (double) right);
  }
}
