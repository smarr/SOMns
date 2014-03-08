package som.primitives.arithmetic;

import java.math.BigInteger;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class ModuloPrim extends ArithmeticPrim {
  @Specialization(order = 1)
  public Object doInteger(final int left, final int right) {
    long l = left;
    long r = right;
    long result = l % r;

    if (l > 0 && r < 0) {
      result += r;
    }
    return intOrBigInt(result);
  }

  @Specialization(order = 2)
  public Object doBigInteger(final BigInteger left, final BigInteger right) {
    return reduceToIntIfPossible(left.mod(right));
  }

  @Specialization(order = 3)
  public double doDouble(final double left, final double right) {
    return left % right;
  }

  @Specialization(order = 10)
  public Object doBigInteger(final BigInteger left, final int right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 11)
  public Object doInteger(final int left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 12)
  public double doInteger(final int left, final double right) {
    return doDouble(left, right);
  }

  @Specialization(order = 13)
  public double doDouble(final double left, final int right) {
    return doDouble(left, right);
  }
}
