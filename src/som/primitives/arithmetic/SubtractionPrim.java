package som.primitives.arithmetic;

import java.math.BigInteger;

import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.dsl.Specialization;


public abstract class SubtractionPrim extends ArithmeticPrim {
  @Specialization(order = 1, rewriteOn = ArithmeticException.class)
  public final long doLong(final long left, final long right) {
    return ExactMath.subtractExact(left, right);
  }

  @Specialization(order = 2)
  public final BigInteger doIntegerWithOverflow(final long left, final long right) {
    return BigInteger.valueOf(left).subtract(BigInteger.valueOf(right));
  }

  @Specialization(order = 20)
  public final Object doBigInteger(final BigInteger left, final BigInteger right) {
    BigInteger result = left.subtract(right);
    return reduceToIntIfPossible(result);
  }

  @Specialization(order = 30)
  public final double doDouble(final double left, final double right) {
    return left - right;
  }

  @Specialization(order = 100)
  public final Object doLong(final long left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 110)
  public final double doInteger(final long left, final double right) {
    return doDouble(left, right);
  }

  @Specialization(order = 120)
  public final Object doBigInteger(final BigInteger left, final long right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 130)
  public final double doDouble(final double left, final long right) {
    return doDouble(left, right);
  }
}
