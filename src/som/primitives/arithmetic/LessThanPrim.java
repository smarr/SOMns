package som.primitives.arithmetic;

import java.math.BigInteger;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class LessThanPrim extends ArithmeticPrim {
  @Specialization(order = 1)
  public final boolean doInteger(final int left, final int right) {
    return left < right;
  }

  @Specialization(order = 20)
  public final boolean doBigInteger(final BigInteger left, final BigInteger right) {
    return left.compareTo(right) < 0;
  }

  @Specialization(order = 30)
  public final boolean doDouble(final double left, final double right) {
    return left < right;
  }

  @Specialization(order = 100)
  public final boolean doInteger(final int left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 110)
  public final boolean doInteger(final int left, final double right) {
    return doDouble(left, right);
  }

  @Specialization(order = 120)
  public final boolean doBigInteger(final BigInteger left, final int right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 130)
  public final boolean doDouble(final double left, final int right) {
    return doDouble(left, (double) right);
  }
}
