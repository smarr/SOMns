package tools.debugger.message;

import org.java_websocket.WebSocket;

import tools.TraceData;
import tools.debugger.FrontendConnector;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.Message.Request;


public class StackTraceRequest extends Request {
  private final long activityId;

  /**
   * Index of the first frame to return.
   */
  private final int startFrame;

  /**
   * Maximum number of frames to return, or all for 0.
   */
  private final int levels;

  public StackTraceRequest(final long activityId, final int startFrame,
      final int levels, final int requestId) {
    super(requestId);
    assert TraceData.isWithinJSIntValueRange(activityId);
    this.activityId = activityId;
    this.startFrame = startFrame;
    this.levels = levels;
  }

  StackTraceRequest() {
    activityId = 0;
    startFrame = 0;
    levels = 0;
  }

  @Override
  public void process(final FrontendConnector connector, final WebSocket conn) {
    Suspension suspension = connector.getSuspension(activityId);
    suspension.sendStackTrace(startFrame, levels, requestId, connector);
  }
}
