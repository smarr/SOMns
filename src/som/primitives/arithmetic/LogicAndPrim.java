package som.primitives.arithmetic;

import java.math.BigInteger;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class LogicAndPrim extends ArithmeticPrim {
  @Specialization(order = 1)
  public final long doLong(final long left, final long right) {
    return left & right;
  }

  @Specialization(order = 2)
  public final Object doBigInteger(final BigInteger left, final BigInteger right) {
    return reduceToIntIfPossible(left.and(right));
  }

  @Specialization(order = 3)
  public final double doDouble(final double receiver, final double right) {
    long left = (long) receiver;
    long rightLong = (long) right;
    return left & rightLong;
  }

  @Specialization(order = 9)
  public final double doDouble(final double receiver, final long right) {
    long left = (long) receiver;
    long rightLong = right;
    return left & rightLong;
  }

  @Specialization(order = 10)
  public final Object doLong(final long left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 11)
  public final Object doBigInteger(final BigInteger left, final long right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 12)
  public final double doLong(final long left, final double right) {
    return doDouble(left, right);
  }
}
