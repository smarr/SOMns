package som.primitives.arithmetic;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class BitXorPrim extends ArithmeticPrim {
  @Specialization
  public final int doInteger(final int receiver, final int right) {
    return receiver ^ right;
  }

  @Specialization
  public final double doDouble(final double receiver, final double right) {
    long left = (long) receiver;
    long rightLong = (long) right;
    return left ^ rightLong;
  }

  @Specialization
  public final double doDouble(final double receiver, final int right) {
    long left = (long) receiver;
    long rightLong = right;
    return left ^ rightLong;
  }
}
