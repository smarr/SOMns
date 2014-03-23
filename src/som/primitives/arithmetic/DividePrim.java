package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.NotYetImplementedException;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class DividePrim extends ArithmeticPrim {
  @Specialization(order = 1)
  public final Object doInteger(final int left, final int right) {
    long result = ((long) left) / right;
    return intOrBigInt(result);
  }

  @Specialization(order = 2)
  public final Object doBigInteger(final BigInteger left, final BigInteger right) {
    BigInteger result = left.divide(right);
    return reduceToIntIfPossible(result);
  }

  @Specialization(order = 10)
  public final Object doBigInteger(final BigInteger left, final int right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 11)
  public final Object doInteger(final int left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 13)
  public final Object doInteger(final int left, final double right) {
    throw new NotYetImplementedException(); // TODO: need to implement the "//" case here directly... : resendAsDouble("//", left, (SDouble) rightObj, frame.pack());
  }
}
