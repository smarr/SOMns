package tools.debugger.message;

import org.java_websocket.WebSocket;

import tools.debugger.FrontendConnector;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.Message.Request;


public final class VariablesRequest extends Request {

  private final int variablesReference;

  VariablesRequest(final int requestId, final int variablesReference) {
    super(requestId);
    this.variablesReference = variablesReference;
  }

  @Override
  public void process(final FrontendConnector connector, final WebSocket conn) {
    Suspension suspension = connector.getSuspensionForGlobalId(variablesReference);
    suspension.sendVariables(variablesReference, connector, requestId);
  }
}
