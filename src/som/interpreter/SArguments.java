package som.interpreter;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public final class SArguments {

  private static final int RCVR_IDX = 0;

  public static Object arg(final VirtualFrame frame, final int index) {
    return frame.getArguments()[index];
  }

  public static Object rcvr(final Frame frame) {
    return frame.getArguments()[RCVR_IDX];
  }

  /**
   * Create a properly encoded SArguments array to be passed via Truffle's API
   * from a receiver object that is separate from the actual arguments.
   */
  public static Object[] createSArgumentsArrayFrom(final Object receiver, final Object[] argsArray) {
    // below, we have a lot of magic numbers and implicit positioning,
    // which are all based on this assumption
    assert RCVR_IDX == 0;

    if (argsArray == null) {
      return new Object[] {receiver};
    }

    Object[] arguments = new Object[argsArray.length + 1];
    arguments[RCVR_IDX] = receiver;

    System.arraycopy(argsArray, 0, arguments, 1, argsArray.length);
    return arguments;
  }

  /**
   * Create a new array from an SArguments array that contains only the true
   * arguments and excludes the receiver. This is used for instance for
   * #doesNotUnderstand (#dnu)
   */
  @ExplodeLoop
  public static Object[] getArgumentsWithoutReceiver(final Object[] arguments) {
    // the code and magic numbers below are based on the following assumption
    assert RCVR_IDX == 0;
//    return Arrays.copyOfRange(arguments, 1, arguments.length);
    Object[] argsArr = new Object[arguments.length - 1];
    for (int i = 1; i < arguments.length; i++) {
      argsArr[i - 1] = arguments[i];
    }
    return argsArr;
  }
}
