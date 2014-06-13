package som.interpreter;

import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public final class SArguments {

  private static final int ENFORCED_FLAG_IDX = 0;
  private static final int DOMAIN_IDX        = 1;
  private static final int RCVR_IDX          = 2;

  private static final int ARGUMENT_OFFSET = RCVR_IDX;

  public static Object arg(final VirtualFrame frame, final int index) {
    return frame.getArguments()[index + ARGUMENT_OFFSET];
  }

  public static Object rcvr(final Frame frame) {
    return frame.getArguments()[RCVR_IDX];
  }

  public static Object rcvr(final Object[] arguments) {
    return arguments[RCVR_IDX];
  }

  public static SObject domain(final Frame frame) {
    return CompilerDirectives.unsafeCast(frame.getArguments()[DOMAIN_IDX], SObject.class, true);
  }

  public static boolean enforced(final Frame frame) {
    return CompilerDirectives.unsafeCast(frame.getArguments()[ENFORCED_FLAG_IDX], Boolean.class, true);
  }

  @ExplodeLoop
  public static Object[] createSArguments(final SObject domain,
      final boolean enforced, final Object[] arguments) {
    Object[] args = new Object[arguments.length + ARGUMENT_OFFSET];
    args[ENFORCED_FLAG_IDX] = enforced;
    args[DOMAIN_IDX]        = domain;

    for (int i = 0; i < arguments.length; i++) {
      args[i + ARGUMENT_OFFSET] = arguments[i];
    }
    return args;
  }

  /**
   * Create a properly encoded SArguments array to be passed via Truffle's API
   * from a receiver object that is separate from the actual arguments.
   */
  @ExplodeLoop
  public static Object[] createSArgumentsWithReceiver(final SObject domain,
      final boolean enforced, final Object receiver, final Object[] argumentsWithoutReceiver) {
    Object[] args = new Object[argumentsWithoutReceiver.length + ARGUMENT_OFFSET + 1];
    args[ENFORCED_FLAG_IDX] = enforced;
    args[DOMAIN_IDX]        = domain;
    args[RCVR_IDX]          = receiver;

    for (int i = 0; i < argumentsWithoutReceiver.length; i++) {
      args[i + ARGUMENT_OFFSET + 1] = argumentsWithoutReceiver[i];
    }
    return args;
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
