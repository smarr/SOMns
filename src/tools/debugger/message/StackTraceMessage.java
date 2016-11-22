package tools.debugger.message;

import java.util.ArrayList;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.source.SourceSection;

import tools.debugger.Suspension;


@SuppressWarnings("unused")
public final class StackTraceMessage extends Message {
  private final StackFrame[] stackFrames;

  /**
   * Total number of frames available.
   */
  private final int totalFrames;

  private final int requestId;

  private StackTraceMessage(final StackFrame[] stackFrames, final int totalFrames, final int requestId) {
    this.stackFrames = stackFrames;
    this.totalFrames = totalFrames;
    this.requestId   = requestId;
  }

  static class StackFrame {
    /**
     * Id for the frame, unique across all threads.
     */
    private final int id;

    /** Name of the frame, typically a method name. */
    private final String name;

    /** Optional source of the frame. */
    private final String sourceUri;

    /** The line within the file of the frame. */
    private final int line;

    /** The column within the line. */
    private final int column;

    /** An optional end line of the range covered by the stack frame. */
    private final int endLine;

    /** An optional end column of the range covered by the stack frame. */
    private final int endColumn;

    StackFrame(final int id, final String name, final String sourceUri,
        final int line, final int column, final int endLine, final int endColumn) {
      this.id        = id;
      this.name      = name;
      this.sourceUri = sourceUri;
      this.line      = line;
      this.column    = column;
      this.endLine   = endLine;
      this.endColumn = endColumn;
    }
  }

  public static StackTraceMessage create(final int startFrame, final int levels,
      final Suspension suspension, final int requestId) {
    ArrayList<DebugStackFrame> frames = suspension.getStackFrames();

    StackFrame[] arr = new StackFrame[Math.min(frames.size(), levels)];

    int skipFrames = suspension.isHaltPrimitive() ? Suspension.FRAMES_SKIPPED_FOR_HALT : 0;
    if (startFrame > skipFrames) {
      skipFrames = startFrame;
    }

    for (int frameId = skipFrames; frameId < frames.size() && frameId < levels; frameId += 1) {
      StackFrame f = createFrame(suspension, frameId, frames.get(frameId));
      arr[frameId - skipFrames] = f;
    }

    return new StackTraceMessage(arr, frames.size(), requestId);
  }

  private static StackFrame createFrame(final Suspension suspension,
      final int frameId, final DebugStackFrame frame) {
    int id = suspension.getGlobalId(frameId);

    String name = frame.getName();
    if (name == null) {
      name = "vm (internal)";
    }

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
