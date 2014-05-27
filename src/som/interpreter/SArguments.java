package som.interpreter;

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class SArguments {

  public static Object arg(final VirtualFrame frame, final int index) {
    return frame.getArguments()[index];
  }

  public static Object rcvr(final VirtualFrame frame) {
    return frame.getArguments()[0];
  }

  public static Object rcvr(final MaterializedFrame frame) {
    return frame.getArguments()[0];
  }
}
