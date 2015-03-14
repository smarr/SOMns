package som.primitives.arithmetic;

import java.math.BigInteger;

import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.utilities.BranchProfile;

public abstract class ModuloPrim extends ArithmeticPrim {
  private final BranchProfile negativeRightOperand = BranchProfile.create();

  @Specialization
  public final double doDouble(final double left, final double right) {
    return left % right;
  }

  @Specialization
  public final double doDouble(final double left, final long right) {
    return doDouble(left, (double) right);
  }

  @Specialization
  public final Object doBigInteger(final BigInteger left, final BigInteger right) {
    return reduceToLongIfPossible(left.mod(right));
  }

  @Specialization
  public final Object doBigInteger(final BigInteger left, final long right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization
  public final Object doLong(final long left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization
  public final double doLong(final long left, final double right) {
    return doDouble(left, right);
  }

  @Specialization(rewriteOn = ArithmeticException.class)
  public final long doLong(final long left, final long right) {
    long l = left;
    long r = right;
    long result = l % r;

    if (l > 0 && r < 0) {
      negativeRightOperand.enter();
      result = ExactMath.addExact(result, r);
    }
    return result;
  }

  public final Object doLongPromotion(final long left, final long right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }
}
