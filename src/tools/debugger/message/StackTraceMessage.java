package tools.debugger.message;

import java.util.ArrayList;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.source.SourceSection;

import tools.debugger.Suspension;

public class StackTraceMessage extends Message {
  StackFrame[] stackFrames;

  /**
   * Total number of frames available.
   */
  int totalFrames;

  int requestId;

  public StackTraceMessage(final StackFrame[] stackFrames, final int totalFrames, final int requestId) {
    this.stackFrames = stackFrames;
    this.totalFrames = totalFrames;
    this.requestId   = requestId;
  }

  static class StackFrame {
    /**
     * Id for the frame, unique across all threads.
     */
    final int id;

    /** Name of the frame, typically a method name. */
    final String name;

    /** Optional source of the frame. */
    final String sourceUri;

    /** The line within the file of the frame. */
    final int line;

    /** The column within the line. */
    final int column;

    /** An optional end line of the range covered by the stack frame. */
    final int endLine;

    /** An optional end column of the range covered by the stack frame. */
    final int endColumn;

    StackFrame(final int id, final String name, final String sourceUri, final int line, final int column, final int endLine, final int endColumn) {
      this.id        = id;
      this.name      = name;
      this.sourceUri = sourceUri;
      this.line      = line;
      this.column    = column;
      this.endLine   = endLine;
      this.endColumn = endColumn;
    }
  }

  private static int getGlobalFrameId(final int frameId, final int activityId) {
    final int maxFrameId = 100000;
    assert frameId < maxFrameId;
    return activityId * maxFrameId + frameId;
  }

  public static StackTraceMessage create(final int startFrame, final int levels,
      final Suspension suspension, final int requestId) {
    SuspendedEvent suspendedEvent = suspension.getEvent();
    int frameId = 0;
    ArrayList<StackFrame> list = new ArrayList<>(levels == 0 ? 10 : levels);

    int skipFrames = SuspendedEventMessage.isHaltPrimitive(suspendedEvent) ? SuspendedEventMessage.FRAMES_SKIPPED_FOR_HALT : 0;
    if (startFrame > skipFrames) {
      skipFrames = startFrame;
    }

    for (DebugStackFrame frame : suspendedEvent.getStackFrames()) {
      if (frameId >= skipFrames && list.size() < levels) {
        StackFrame f = createFrame(suspension.activityId, frameId, frame);
        list.add(f);
      }

      frameId += 1;
    }

    assert list.size() <= levels;
    return new StackTraceMessage(list.toArray(new StackFrame[0]), frameId, requestId);
  }

  private static StackFrame createFrame(final int activityId,
      final int frameId, final DebugStackFrame frame) {
    int id = getGlobalFrameId(frameId, activityId);

    String name = frame.getName();
    SourceSection ss = frame.getSourceSection();
    String sourceUri;
    int line;
    int column;
    int endLine;
    int endColumn;
    if (ss != null) {
      sourceUri = ss.getSource().getURI().toString();
      line      = ss.getStartLine();
      column    = ss.getStartColumn();
      endLine   = ss.getEndLine();
      endColumn = ss.getEndColumn();
    } else {
      sourceUri = null;
      line      = 0;
      column    = 0;
      endLine   = 0;
      endColumn = 0;
    }
    StackFrame f = new StackFrame(id, name, sourceUri, line, column, endLine, endColumn);
    return f;
  }
}
