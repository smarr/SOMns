package tools.debugger.message;

import org.java_websocket.WebSocket;

import tools.debugger.FrontendConnector;
import tools.debugger.message.Message.IncommingMessage;
import tools.debugger.breakpoints.BreakpointInfo;


public class InitializeConnection extends IncommingMessage {

  private final BreakpointInfo[] breakpoints;

  public InitializeConnection(final BreakpointInfo[] breakpoints) {
    this.breakpoints = breakpoints;
  }

  InitializeConnection() {
    this.breakpoints = null;
  }

  public BreakpointInfo[] getBreakpoints() {
    return breakpoints;
  }

  @Override
  public void process(final FrontendConnector connector, final WebSocket conn) {
    if (breakpoints != null) {
      for (BreakpointInfo bp : breakpoints) {
        bp.registerOrUpdate(connector);
      }
    }
    connector.completeConnection(conn);
  }
}
