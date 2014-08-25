package som.interpreter;

import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class SArguments {

  private static final int ENFORCED_FLAG_IDX = 0;
  private static final int DOMAIN_IDX        = 1;
  private static final int RCVR_IDX          = 2;

  private static final int ARGUMENT_OFFSET = RCVR_IDX;

  private static Object[] args(final VirtualFrame frame) {
    return CompilerDirectives.unsafeCast(frame.getArguments(), Object[].class, true, true);
  }

  public static Object arg(final VirtualFrame frame, final int index) {
    return CompilerDirectives.unsafeCast(args(frame)[index + ARGUMENT_OFFSET], Object.class, true, true);
  }

  public static Object rcvr(final Frame frame) {
    return rcvr(frame.getArguments());
  }

  public static Object rcvr(final Object[] arguments) {
    return CompilerDirectives.unsafeCast(arguments[RCVR_IDX], Object.class, true, true);
  }

  public static SObject domain(final Frame frame) {
    assert frame.getArguments()[DOMAIN_IDX] != null;
    return CompilerDirectives.unsafeCast(frame.getArguments()[DOMAIN_IDX], SObject.class, true, true);
  }

  public static boolean enforced(final Object[] arguments) {
    return CompilerDirectives.unsafeCast(arguments[ENFORCED_FLAG_IDX], Boolean.class, true, true);
  }

  public static boolean enforced(final Frame frame) {
    return enforced(frame.getArguments());
  }

  public static Object[] createSArgumentsArray(final Object... arguments) {
    assert arguments.length >= 3;
    assert arguments[ENFORCED_FLAG_IDX] instanceof Boolean;
    assert arguments[DOMAIN_IDX]        instanceof SObject;
    assert arguments[RCVR_IDX] != null;
    return arguments;
  }

  public static Object[] createSArguments(final SObject domain,
      final boolean enforced, final Object[] arguments) {
    Object[] args = new Object[arguments.length + ARGUMENT_OFFSET];
    args[ENFORCED_FLAG_IDX] = enforced;
    args[DOMAIN_IDX]        = domain;

    System.arraycopy(arguments, 0, args, ARGUMENT_OFFSET, arguments.length);
    return args;
  }

  /**
   * Create a properly encoded SArguments array to be passed via Truffle's API
   * from a receiver object that is separate from the actual arguments.
   */
  public static Object[] createSArgumentsWithReceiver(final SObject domain,
      final boolean enforced, final Object receiver, final Object[] argumentsWithoutReceiver) {
    Object[] args = new Object[argumentsWithoutReceiver.length + ARGUMENT_OFFSET];  // + 1
    args[ENFORCED_FLAG_IDX] = enforced;
    args[DOMAIN_IDX]        = domain;
    args[RCVR_IDX]          = receiver;

    if (argumentsWithoutReceiver.length > 1) {
      System.arraycopy(argumentsWithoutReceiver, 1, args, ARGUMENT_OFFSET + 1, argumentsWithoutReceiver.length - 1);
    }
    return args;
  }
}
