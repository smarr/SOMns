package som.primitives.arithmetic;

import java.math.BigInteger;

import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.dsl.Specialization;


public abstract class SubtractionPrim extends ArithmeticPrim {
  @Specialization(rewriteOn = ArithmeticException.class)
  public final long doLong(final long left, final long right) {
    return ExactMath.subtractExact(left, right);
  }

  @Specialization
  public final BigInteger doLongWithOverflow(final long left, final long right) {
    return BigInteger.valueOf(left).subtract(BigInteger.valueOf(right));
  }

  @Specialization
  public final Object doBigInteger(final BigInteger left, final BigInteger right) {
    BigInteger result = left.subtract(right);
    return reduceToLongIfPossible(result);
  }

  @Specialization
  public final double doDouble(final double left, final double right) {
    return left - right;
  }

  @Specialization
  public final Object doLong(final long left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization
  public final double doLong(final long left, final double right) {
    return doDouble(left, right);
  }

  @Specialization
  public final Object doBigInteger(final BigInteger left, final long right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization
  public final double doDouble(final double left, final long right) {
    return doDouble(left, (double) right);
  }
}
