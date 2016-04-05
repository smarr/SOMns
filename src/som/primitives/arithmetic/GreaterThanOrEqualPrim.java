package som.primitives.arithmetic;

import java.math.BigInteger;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.primitives.ComparisonPrim;


public abstract class GreaterThanOrEqualPrim extends ComparisonPrim {
  protected GreaterThanOrEqualPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }
  protected GreaterThanOrEqualPrim(final SourceSection source) { super(false, source); }

  @Specialization
  public final boolean doLong(final long left, final long right) {
    return left >= right;
  }

  @Specialization
  public final boolean doBigInteger(final BigInteger left, final BigInteger right) {
    return left.compareTo(right) >= 0;
  }

  @Specialization
  public final boolean doDouble(final double left, final double right) {
    return left >= right;
  }

  @Specialization
  public final boolean doLong(final long left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization
  public final boolean doLong(final long left, final double right) {
    return doDouble(left, right);
  }

  @Specialization
  public final boolean doBigInteger(final BigInteger left, final long right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization
  public final boolean doDouble(final double left, final long right) {
    return doDouble(left, (double) right);
  }
}
