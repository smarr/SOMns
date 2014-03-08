package som.primitives.arithmetic;

import java.math.BigInteger;

import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.dsl.Specialization;


public abstract class MultiplicationPrim extends ArithmeticPrim {
  @Specialization(order = 1, rewriteOn = ArithmeticException.class)
  public int doInteger(final int left, final int right) {
    return ExactMath.multiplyExact(left, right);
  }

  @Specialization(order = 2)
  public Object doIntegerWithOverflow(final int left, final int right) {
    long result = ((long) left) * right;
    return intOrBigInt(result);
  }

  @Specialization(order = 5)
  public Object doBigInteger(final BigInteger left, final BigInteger right) {
    BigInteger result = left.multiply(right);
    return reduceToIntIfPossible(result);
  }

  @Specialization(order = 6)
  public double doDouble(final double left, final double right) {
    return left * right;
  }

  @Specialization(order = 10)
  public Object doInteger(final int left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 11)
  public double doInteger(final int left, final double right) {
    return doDouble(left, right);
  }

  @Specialization(order = 12)
  public Object doBigInteger(final BigInteger left, final int right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 13)
  public double doDouble(final double left, final int right) {
    return doDouble(left, (double) right);
  }
}
