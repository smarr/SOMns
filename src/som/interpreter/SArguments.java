package som.interpreter;

import som.vmobjects.SObject;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

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

  public static SObject domain(final Frame frame) {
    return (SObject) frame.getArguments()[DOMAIN_IDX];
  }

  public static boolean enforced(final Frame frame) {
    return (boolean) frame.getArguments()[ENFORCED_FLAG_IDX];
  }
}
