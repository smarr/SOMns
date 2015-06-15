package som.interpreter;

import som.primitives.arrays.NewPrim.AllocProfile;
import som.vmobjects.SArray;

import com.oracle.truffle.api.frame.Frame;

public final class SArguments {

  public static final int RCVR_IDX = 0;

  private static Object[] args(final Frame frame) {
    return frame.getArguments();
  }

  public static Object arg(final Frame frame, final int index) {
    return args(frame)[index];
  }

  public static Object rcvr(final Frame frame) {
    return arg(frame, RCVR_IDX);
  }

  /**
   * Create a new array from an SArguments array that contains only the true
   * arguments and excludes the receiver. This is used for instance for
   * #doesNotUnderstand (#dnu)
   */
  public static SArray getArgumentsWithoutReceiver(final Object[] arguments) {
    // the code and magic numbers below are based on the following assumption
    assert RCVR_IDX == 0;
    assert arguments.length >= 1;  // <- that's the receiver
    Object[] argsArr = new Object[arguments.length - 1];
    if (argsArr.length == 0) {
      return new SArray(0, emptyArrProfile);
    }
    System.arraycopy(arguments, 1, argsArr, 0, argsArr.length);
    return new SArray(argsArr);
  }

  private static final AllocProfile emptyArrProfile = new AllocProfile();
}
