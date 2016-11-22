package tools.debugger.message;

import org.java_websocket.WebSocket;

import tools.debugger.FrontendConnector;
import tools.debugger.Suspension;


public final class ScopesRequest extends Respond {

  private final int requestId;
  private final int frameId;

  ScopesRequest(final int requestId, final int frameId) {
    this.requestId = requestId;
    this.frameId   = frameId;
  }

  @Override
  public void process(final FrontendConnector connector, final WebSocket conn) {
    Suspension suspension = connector.getSuspensionForGlobalId(frameId);
    suspension.sendScopes(frameId, connector, requestId);
  }
}
