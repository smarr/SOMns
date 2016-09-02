package som.primitives.arithmetic;

import java.math.BigInteger;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.primitives.Primitive;


@GenerateNodeFactory
@Primitive(primitive = "int:modulo:")
@Primitive(primitive = "double:modulo:")
@Primitive(selector = "%")
public abstract class ModuloPrim extends ArithmeticPrim {
  protected ModuloPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }
  protected ModuloPrim(final SourceSection source) { super(false, source); }

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

  @Specialization
  public final long doLong(final long left, final long right) {
    return Math.floorMod(left, right);
  }

  public final Object doLongPromotion(final long left, final long right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }
}
