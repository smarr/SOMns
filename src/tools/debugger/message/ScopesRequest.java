package tools.debugger.message;

import org.java_websocket.WebSocket;

import tools.debugger.FrontendConnector;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.Message.Request;


public final class ScopesRequest extends Request {
  private final int frameId;

  ScopesRequest(final int requestId, final int frameId) {
    super(requestId);
    this.frameId   = frameId;
  }

  @Override
  public void process(final FrontendConnector connector, final WebSocket conn) {
    Suspension suspension = connector.getSuspensionForGlobalId(frameId);
    suspension.sendScopes(frameId, connector, requestId);
  }
}
