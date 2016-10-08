package tools.debugger.message;

import org.java_websocket.WebSocket;

import tools.debugger.FrontendConnector;


public abstract class Respond {
  public abstract void process(final FrontendConnector connector, final WebSocket conn);
}
