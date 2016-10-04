package tools.debugger.message;

import org.java_websocket.WebSocket;

import tools.debugger.WebSocketHandler;
import tools.debugger.session.BreakpointInfo;


public class UpdateBreakpoint extends Respond {
  private final BreakpointInfo breakpoint;

  public UpdateBreakpoint(final BreakpointInfo breakpoint) {
    this.breakpoint = breakpoint;
  }

  public BreakpointInfo getBreakpoint() {
    return breakpoint;
  }

  @Override
  public void process(final WebSocketHandler handler, final WebSocket conn) {
    handler.onBreakpointUpdate(breakpoint);
  }
}
