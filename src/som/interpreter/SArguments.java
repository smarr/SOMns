package som.interpreter;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.Frame;

public final class SArguments {

  public static final int RCVR_IDX = 0;

  private static Object[] args(final Frame frame) {
    return CompilerDirectives.unsafeCast(frame.getArguments(), Object[].class, true, true);
  }

  public static Object arg(final Frame frame, final int index) {
    return CompilerDirectives.unsafeCast(args(frame)[index], Object.class, true, true);
  }

  public static Object rcvr(final Frame frame) {
    return arg(frame, RCVR_IDX);
  }

  /**
   * Create a properly encoded SArguments array to be passed via Truffle's API
   * from a receiver object that is separate from the actual arguments.
   */
  public static Object[] createSArgumentsArrayFrom(final Object receiver, final Object[] argsArray) {
    // below, we have a lot of magic numbers and implicit positioning,
    // which are all based on this assumption
    assert RCVR_IDX == 0;

    if (argsArray == null || argsArray.length == 0) {
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
  public static Object[] getArgumentsWithoutReceiver(final Object[] arguments) {
    // the code and magic numbers below are based on the following assumption
    assert RCVR_IDX == 0;
    assert arguments.length >= 1;  // <- that's the receiver
    Object[] argsArr = new Object[arguments.length - 1];
    if (argsArr.length == 0) {
      return argsArr;
    }
    System.arraycopy(arguments, 1, argsArr, 0, argsArr.length);
    return argsArr;
  }
}
