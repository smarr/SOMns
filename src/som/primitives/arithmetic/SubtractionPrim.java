package som.primitives.arithmetic;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.vmobjects.SSymbol;
import som.vmobjects.SType;


@GenerateNodeFactory
@Primitive(primitive = "int:subtract:")
@Primitive(primitive = "double:subtract:")
@Primitive(selector = "-")
public abstract class SubtractionPrim extends ArithmeticPrim {
  @Specialization(rewriteOn = ArithmeticException.class)
  public final long doLong(final long left, final long right) {
    return Math.subtractExact(left, right);
  }

  @Specialization
  @TruffleBoundary
  public final BigInteger doLongWithOverflow(final long left, final long right) {
    return BigInteger.valueOf(left).subtract(BigInteger.valueOf(right));
  }

  @Specialization
  @TruffleBoundary
  public final Object doBigInteger(final BigInteger left, final BigInteger right) {
    BigInteger result = left.subtract(right);
    return reduceToLongIfPossible(result);
  }

  @Specialization
  public final double doDouble(final double left, final double right) {
    return left - right;
  }

  @Specialization
  @TruffleBoundary
  public final Object doLong(final long left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization
  public final double doLong(final long left, final double right) {
    return doDouble(left, right);
  }

  @Specialization
  @TruffleBoundary
  public final Object doBigInteger(final BigInteger left, final long right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization
  public final double doDouble(final double left, final long right) {
    return doDouble(left, (double) right);
  }

  @Specialization
  @TruffleBoundary
  public final SType doTypeSubtraction(final SType left, final SType right) {
    Set<SSymbol> signatures = new HashSet<>();
    signatures.addAll(Arrays.asList(left.getSignatures()));
    signatures.removeAll(Arrays.asList(right.getSignatures()));
    return new SType.InterfaceType(signatures.toArray(new SSymbol[signatures.size()]));
  }
}
