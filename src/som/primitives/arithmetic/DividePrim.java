package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.NotYetImplementedException;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class DividePrim extends ArithmeticPrim {
  @Specialization
  public final long doLong(final long left, final long right) {
    return left / right;
  }

  @Specialization
  public final Object doBigInteger(final BigInteger left, final BigInteger right) {
    BigInteger result = left.divide(right);
    return reduceToLongIfPossible(result);
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
  public final Object doLong(final long left, final double right) {
    CompilerAsserts.neverPartOfCompilation("DividePrim");
    throw new NotYetImplementedException(); // TODO: need to implement the "//" case here directly... : resendAsDouble("//", left, (SDouble) rightObj, frame.pack());
  }
}
