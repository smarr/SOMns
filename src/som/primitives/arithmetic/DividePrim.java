package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.NotYetImplementedException;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class DividePrim extends ArithmeticPrim {
  @Specialization(order = 1)
  public final long doLong(final long left, final long right) {
    return left / right;
  }

  @Specialization(order = 2)
  public final Object doBigInteger(final BigInteger left, final BigInteger right) {
    BigInteger result = left.divide(right);
    return reduceToIntIfPossible(result);
  }

  @Specialization(order = 10)
  public final Object doBigInteger(final BigInteger left, final long right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 11)
  public final Object doLong(final long left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 13)
  public final Object doLong(final long left, final double right) {
    CompilerAsserts.neverPartOfCompilation();
    throw new NotYetImplementedException(); // TODO: need to implement the "//" case here directly... : resendAsDouble("//", left, (SDouble) rightObj, frame.pack());
  }
}
