package som.interpreter;

import com.oracle.truffle.api.Arguments;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class SArguments extends Arguments {
  private final Object   receiver;
  private final Object[] arguments;

  public SArguments(final Object receiver, final Object[] arguments) {
    this.receiver  = receiver;
    this.arguments = arguments;
  }

  public Object getReceiver() {
    return receiver;
  }

  public Object[] getArguments() {
    return arguments;
  }

  public static Object[] getArgumentsFromFrame(final VirtualFrame frame) {
    return frame.getArguments(SArguments.class).arguments;
  }

  public static Object getReceiverFromFrame(final VirtualFrame frame) {
    return frame.getArguments(SArguments.class).receiver;
  }
}
