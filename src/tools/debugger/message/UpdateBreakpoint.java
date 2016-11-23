package tools.debugger.message;

import org.java_websocket.WebSocket;

import tools.debugger.FrontendConnector;
import tools.debugger.message.Message.IncommingMessage;
import tools.debugger.session.BreakpointInfo;


public class UpdateBreakpoint extends IncommingMessage {
  private final BreakpointInfo breakpoint;

  public UpdateBreakpoint(final BreakpointInfo breakpoint) {
    this.breakpoint = breakpoint;
  }

  public BreakpointInfo getBreakpoint() {
    return breakpoint;
  }

  @Override
  public void process(final FrontendConnector connector, final WebSocket conn) {
    breakpoint.registerOrUpdate(connector);
  }
}
