package som.primitives.arithmetic;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class BitXorPrim extends ArithmeticPrim {
  @Specialization
  public final long doLong(final long receiver, final long right) {
    return receiver ^ right;
  }

  @Specialization
  public final double doDouble(final double receiver, final double right) {
 // TODO: on double? this feels wrong!!
    long left = (long) receiver;
    long rightLong = (long) right;
    return left ^ rightLong;
  }

  @Specialization
  public final double doDouble(final double receiver, final long right) {
    // TODO: on double? this feels wrong!!
    long left = (long) receiver;
    long rightLong = right;
    return left ^ rightLong;
  }
}
