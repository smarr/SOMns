package tools.debugger.message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.primitives.ObjectPrims.HaltPrim;
import tools.SourceCoordinate;
import tools.SourceCoordinate.FullSourceCoordinate;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.Message.OutgoingMessage;


@SuppressWarnings({"unused", "deprecation"})
public class SuspendedEventMessage extends OutgoingMessage {

  public static SuspendedEventMessage create(final SuspendedEvent e,
      final String eventId) {
    String sourceUri  = getSuspendedSourceUri(e);
    Frame[] frames    = getStack(e);
    TopFrame topFrame = getTopFrame(e, e.getFrame());

    return new SuspendedEventMessage(sourceUri, eventId, getStack(e), topFrame);
  }

  private final String id;
  private final String sourceUri;

  private final Frame[] stack;
  private final TopFrame topFrame;

  protected SuspendedEventMessage(final String uri, final String eventId,
      final Frame[] stack, final TopFrame topFrame) {
    this.sourceUri = uri;
    this.id        = eventId;
    this.stack     = stack;
    this.topFrame  = topFrame;
  }

  protected static class Frame {
    private final FullSourceCoordinate sourceSection;
    private final String methodName;

    protected Frame(final FullSourceCoordinate sourceSection,
        final String methodName) {
      this.sourceSection = sourceSection;
      this.methodName    = methodName;
    }
  }

  protected static class TopFrame {
    private final String[] arguments;
    private final Map<String, String> slots;

    protected TopFrame(final String[] arguments, final Map<String, String> slots) {
      this.arguments = arguments;
      this.slots     = slots;
    }
  }

  private static TopFrame getTopFrame(final SuspendedEvent e,
      final MaterializedFrame topFrame) {
    com.oracle.truffle.api.frame.Frame[] frame = new com.oracle.truffle.api.frame.Frame[1];
    if (isHaltPrimitive(e)) {
      int[] skipFrames = new int[]{Suspension.FRAMES_SKIPPED_FOR_HALT};

      Truffle.getRuntime().iterateFrames(frameInstance -> {
        if (skipFrames[0] > 0) {
          skipFrames[0] -= 1;
          return null;
        }
        frame[0] = frameInstance.getFrame(FrameAccess.READ_ONLY, true);
        return frameInstance;
      });
    } else {
      frame[0] = topFrame;
    }

    Object[] arguments = frame[0].getArguments();
    String[] args = new String[arguments.length];

    for (int i = 0; i < arguments.length; i += 1) {
      args[i] = Objects.toString(arguments[i]);
    }

    Map<String, String> slots = new HashMap<>();

    for (DebugValue v : e.getTopStackFrame()) {
      slots.put(v.getName(), v.as(String.class));
    }

    return new TopFrame(args, slots);
  }

  private static Frame[] getStack(final SuspendedEvent e) {
    int skipFrames = 0;
    if (isHaltPrimitive(e)) {
      skipFrames = 2;
    }

    ArrayList<Frame> frames = new ArrayList<>();
    for (DebugStackFrame f : e.getStackFrames()) {
      if (skipFrames > 0) {
        skipFrames -= 1;
        continue;
      }
      frames.add(new Frame(SourceCoordinate.create(f.getSourceSection()),
          f.getName()));
    }
    return frames.toArray(new Frame[0]);
  }

  /**
   * @deprecated because it should be only implemented in {@link Suspension} but requires a few more changes
   */
  @Deprecated
  public static boolean isHaltPrimitive(final SuspendedEvent e) {
    return e.getNode() instanceof HaltPrim;
  }

  private static String getSuspendedSourceUri(final SuspendedEvent e) {
    SourceSection suspendedSection =  e.getSourceSection();
    assert suspendedSection != null : "TODO: what is this case, was supported before. Not sure whether the top frame has another source section";
    String sourceUri = suspendedSection.getSource().getURI().toString();
    return sourceUri;
  }
}
