package tools.debugger.message;

import org.java_websocket.WebSocket;

import tools.debugger.WebSocketHandler;


public abstract class Respond {
  public abstract void process(final WebSocketHandler handler, final WebSocket conn);
}
