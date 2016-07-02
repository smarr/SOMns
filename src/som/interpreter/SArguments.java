package som.interpreter;

import com.oracle.truffle.api.frame.Frame;

import som.vm.constants.Classes;
import som.vmobjects.SArray.SImmutableArray;

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
  public static SImmutableArray getArgumentsWithoutReceiver(final Object[] arguments) {
    if (arguments.length == 1) {
      return new SImmutableArray(0, Classes.valueArrayClass);
    }

    Object[] argsArr = getPlainArgumentWithoutReceiver(arguments);
    return new SImmutableArray(argsArr, Classes.valueArrayClass);
  }

  public static Object[] getPlainArgumentWithoutReceiver(final Object[] arguments) {
    int rcvrIdx = 0; // the code and magic numbers below are based on the following assumption
    assert RCVR_IDX == rcvrIdx;
    assert arguments.length >= 1;  // <- that's the receiver
    Object[] argsArr = new Object[arguments.length - 1];

    System.arraycopy(arguments, 1, argsArr, 0, argsArr.length);
    return argsArr;
  }
}
