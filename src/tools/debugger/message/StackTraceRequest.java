package tools.debugger.message;

import org.java_websocket.WebSocket;

import tools.debugger.FrontendConnector;
import tools.debugger.Suspension;


public class StackTraceRequest extends Respond {

  private int activityId;

  /**
   * Index of the first frame to return.
   */
  private int startFrame;

  /**
   * Maximum number of frames to return, or all for 0.
   */
  private int levels;

  /**
   * Id to connect request and responds.
   */
  private int requestId;

  public StackTraceRequest(final int activityId, final int startFrame,
      final int levels, final int requestId) {
    this.activityId = activityId;
    this.startFrame = startFrame;
    this.levels     = levels;
    this.requestId  = requestId;
  }

  @Override
  public void process(final FrontendConnector connector, final WebSocket conn) {
    Suspension suspension = connector.getSuspension(activityId);
    suspension.sendStackTrace(startFrame, levels, requestId, connector);
  }
}
