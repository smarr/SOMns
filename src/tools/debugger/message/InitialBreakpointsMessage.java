package tools.debugger.message;

import org.java_websocket.WebSocket;

import tools.debugger.FrontendConnector;
import tools.debugger.message.Message.IncommingMessage;
import tools.debugger.session.BreakpointInfo;


public class InitialBreakpointsMessage extends IncommingMessage {
  private final BreakpointInfo[] breakpoints;

  /**
   * The client is using a protocol similar to VS code.
   */
  private final boolean debuggerProtocol;

  public InitialBreakpointsMessage(final BreakpointInfo[] breakpoints) {
    this.breakpoints = breakpoints;
    this.debuggerProtocol = false;
  }

  public BreakpointInfo[] getBreakpoints() {
    return breakpoints;
  }

  @Override
  public void process(final FrontendConnector connector, final WebSocket conn) {
    for (BreakpointInfo bp : breakpoints) {
      bp.registerOrUpdate(connector);
    }
    connector.completeConnection(conn, debuggerProtocol);
  }
}
