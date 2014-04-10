package som.interpreter;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class SArguments {

  public static Object arg(final VirtualFrame frame, final int index) {
    return frame.getArguments()[index];
  }

  public static Object rcvr(final VirtualFrame frame) {
    return frame.getArguments()[0];
  }
}
