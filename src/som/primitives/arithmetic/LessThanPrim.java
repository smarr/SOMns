package som.primitives.arithmetic;

import java.math.BigInteger;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.primitives.ComparisonPrim;
import som.primitives.Primitive;


@GenerateNodeFactory
@Primitive(primitive = "int:lessThan:")
@Primitive(primitive = "double:lessThan:")
@Primitive(selector = "<")
public abstract class LessThanPrim extends ComparisonPrim {
  protected LessThanPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }
  protected LessThanPrim(final SourceSection source) { super(false, source); }

  @Specialization
  public final boolean doLong(final long left, final long right) {
    return left < right;
  }

  @Specialization
  public final boolean doBigInteger(final BigInteger left, final BigInteger right) {
    return left.compareTo(right) < 0;
  }

  @Specialization
  public final boolean doDouble(final double left, final double right) {
    return left < right;
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
