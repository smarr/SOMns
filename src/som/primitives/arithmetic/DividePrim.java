package som.primitives.arithmetic;

import java.math.BigInteger;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;


@GenerateNodeFactory
@Primitive(primitive = "int:divideBy:", selector = "/")
public abstract class DividePrim extends ArithmeticPrim {
  private static final BigInteger OVERFLOW_RESULT =
      BigInteger.valueOf(Long.MIN_VALUE).divide(BigInteger.valueOf(-1));

  @Specialization(guards = "!isOverflowDivision(left, right)")
  public final long doLong(final long left, final long right) {
    return left / right;
  }

  @Specialization(guards = "isOverflowDivision(left, right)")
  public final Object doLongWithOverflow(final long left, final long right) {
    return OVERFLOW_RESULT;
  }

  @Specialization
  @TruffleBoundary
  public final Object doBigInteger(final BigInteger left, final BigInteger right) {
    BigInteger result = left.divide(right);
    return reduceToLongIfPossible(result);
  }

  @Specialization
  @TruffleBoundary
  public final Object doBigInteger(final BigInteger left, final long right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization
  @TruffleBoundary
  public final Object doLong(final long left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization
  public final Object doLong(final long left, final double right) {
    return (long) (left / right);
  }

  protected static final boolean isOverflowDivision(final long left, final long right) {
    return left == Long.MIN_VALUE && right == -1;
  }
}
