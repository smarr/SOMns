package tools.debugger.message;

import org.java_websocket.WebSocket;

import tools.debugger.WebSocketHandler;
import tools.debugger.session.BreakpointInfo;


public class InitialBreakpointsResponds extends Respond {
  private final BreakpointInfo[] breakpoints;

  public InitialBreakpointsResponds(final BreakpointInfo[] breakpoints) {
    this.breakpoints = breakpoints;
  }

  public BreakpointInfo[] getBreakpoints() {
    return breakpoints;
  }

  public void process(final WebSocketHandler handler, final WebSocket conn) {
    handler.onInitialBreakpoints(conn, breakpoints);
  }
}
