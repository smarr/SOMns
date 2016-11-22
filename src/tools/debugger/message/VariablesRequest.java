package tools.debugger.message;

import org.java_websocket.WebSocket;

import tools.debugger.FrontendConnector;
import tools.debugger.Suspension;


public final class VariablesRequest extends Respond {

  private final int requestId;
  private final int variablesReference;

  VariablesRequest(final int requestId, final int variablesReference) {
    this.requestId = requestId;
    this.variablesReference = variablesReference;
  }

  @Override
  public void process(final FrontendConnector connector, final WebSocket conn) {
    Suspension suspension = connector.getSuspensionForGlobalId(variablesReference);
    suspension.sendVariables(variablesReference, connector, requestId);
  }

}
