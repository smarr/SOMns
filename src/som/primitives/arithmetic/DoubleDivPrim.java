package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.NotYetImplementedException;
import som.vmobjects.SAbstractObject;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class DoubleDivPrim extends ArithmeticPrim {
  @Specialization(order = 1)
  public final double doDouble(final double left, final double right) {
    return left / right;
  }

  @Specialization(order = 2)
  public final double doLong(final long left, final long right) {
    return ((double) left) / right;
  }

  @Specialization(order = 10)
  public final double doDouble(final double left, final long right) {
    return doDouble(left, (double) right);
  }

  @Specialization(order = 100)
  public final SAbstractObject doLong(final long left, final BigInteger right) {
    throw new NotYetImplementedException(); // TODO: need to implement the "/" case here directly... : return resendAsBigInteger("/", left, (SBigInteger) rightObj, frame.pack());
  }

  @Specialization(order = 101)
  public final SAbstractObject doLong(final long left, final double right) {
    throw new NotYetImplementedException(); // TODO: need to implement the "/" case here directly... : return resendAsDouble("/", left, (SDouble) rightObj, frame.pack());
  }
}
