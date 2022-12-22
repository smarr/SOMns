package tools.debugger.message;

import java.util.ArrayList;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.Actor.ExecutorRootNode;
import som.interpreter.actors.ReceivedRootNode;
import tools.TraceData;
import tools.debugger.entities.EntityType;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.Message.Response;


@SuppressWarnings("unused")
public final class StackTraceResponse extends Response {
  private final StackFrame[] stackFrames;

  // TODO: we should perhaps move that into the stack frames to have more precise info
  // but that would make tracking more difficult
  private final byte[] concurrentEntityScopes;
  private final long   activityId;

  /**
   * Total number of frames available.
   */
  private final int totalFrames;

  private StackTraceResponse(final long activityId,
      final StackFrame[] stackFrames, final int totalFrames,
      final int requestId, final byte[] concurrentEntityScopes) {
    super(requestId);
    assert TraceData.isWithinJSIntValueRange(activityId);
    this.activityId = activityId;
    this.stackFrames = stackFrames;
    this.totalFrames = totalFrames;
    this.concurrentEntityScopes = concurrentEntityScopes;

    boolean assertsOn = false;
    assert assertsOn = true;

    if (assertsOn) {
      for (StackFrame sf : stackFrames) {
        assert sf != null;
      }
    }
  }

  private static class StackFrame {
    /**
     * Id for the frame, unique across all threads.
     */
    private final long id;

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

    /** An optional number of characters in the range. */
    private final int length;

    private final long frameId;

    StackFrame(final long globalId, final long frameId, final String name, final String sourceUri,
        final int line, final int column, final int endLine,
        final int endColumn, final int length) {
      assert TraceData.isWithinJSIntValueRange(globalId);
      this.id = globalId;
      this.frameId = frameId;
      this.name = name;
      this.sourceUri = sourceUri;
      this.line = line;
      this.column = column;
      this.endLine = endLine;
      this.endColumn = endColumn;
      this.length = length;
    }
  }

  private static int getNumRootNodesToSkip(final ArrayList<DebugStackFrame> frames) {
    int skip = 0;
    int size = frames.size();

    // Actor-specific infrastructure, to be skipped from stack traces
    if (frames.get(size - 1).getRootNode() instanceof ExecutorRootNode) {
      skip += 1;
    }

    // Actor-specific infrastructure, to be skipped from stack traces
    if (size >= 2 && frames.get(size - 2).getRootNode() instanceof ReceivedRootNode) {
      skip += 1;
    }

    return skip;
  }

  public static StackTraceResponse create(final int startFrame, final int levels,
      final Suspension suspension, final int requestId) {
    ArrayList<DebugStackFrame> frames = suspension.getStackFrames();
    int skipFrames = suspension.getFrameSkipCount();

    if (startFrame > skipFrames) {
      skipFrames = startFrame;
    }

    int numFrames = levels;
    if (numFrames == 0) {
      numFrames = Integer.MAX_VALUE;
    }
    numFrames = Math.min(frames.size(), numFrames);
    numFrames -= skipFrames + getNumRootNodesToSkip(frames);

    StackFrame[] arr = new StackFrame[numFrames];

    for (int i = 0; i < numFrames; i += 1) {
      int frameId = i + skipFrames;
      assert !(frames.get(
          frameId).getRootNode() instanceof ReceivedRootNode)
          : "This should have been skipped in the code above";
      StackFrame f = createFrame(suspension, frameId, frames.get(frameId));
      arr[i] = f;
    }

    EntityType[] concEntityScopes = suspension.getCurrentEntityScopes();

    return new StackTraceResponse(suspension.activityId, arr, frames.size(),
        requestId, EntityType.getIds(concEntityScopes));
  }

  private static StackFrame createFrame(final Suspension suspension,
      final int frameId, final DebugStackFrame frame) {
    long id = suspension.getGlobalId(frameId);

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
    int length;
    if (ss != null) {
      sourceUri = ss.getSource().getURI().toString();
      line = ss.getStartLine();
      column = ss.getStartColumn();
      endLine = ss.getEndLine();
      endColumn = ss.getEndColumn();
      length = ss.getCharLength();
    } else {
      sourceUri = null;
      line = 0;
      column = 0;
      endLine = 0;
      endColumn = 0;
      length = 0;
    }
    return new StackFrame(id, frameId, name, sourceUri, line, column, endLine, endColumn, length);
  }
}
