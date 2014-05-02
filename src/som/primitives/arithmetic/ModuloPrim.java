package som.primitives.arithmetic;

import java.math.BigInteger;

import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class ModuloPrim extends ArithmeticPrim {
  @Specialization(order = 1, rewriteOn = ArithmeticException.class)
  public final long doLong(final long left, final long right) {
    long l = left;
    long r = right;
    long result = l % r;

    if (l > 0 && r < 0) {
      result = ExactMath.addExact(result, r);
    }
    return result;
  }

  @Specialization(order = 2)
  public final Object doBigInteger(final BigInteger left, final BigInteger right) {
    return reduceToIntIfPossible(left.mod(right));
  }

  @Specialization(order = 3)
  public final double doDouble(final double left, final double right) {
    return left % right;
  }

  @Specialization(order = 10)
  public final Object doBigInteger(final BigInteger left, final long right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 11)
  public final Object doLong(final long left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 12)
  public final double doLong(final long left, final double right) {
    return doDouble(left, right);
  }

  @Specialization(order = 13)
  public final double doDouble(final double left, final long right) {
    return doDouble(left, right);
  }
}
