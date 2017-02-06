package tools.debugger.message;

import org.java_websocket.WebSocket;

import tools.debugger.FrontendConnector;
import tools.debugger.message.Message.IncommingMessage;


public final class ProgramInfoRequest extends IncommingMessage {

  @Override
  public void process(final FrontendConnector connector, final WebSocket conn) {
    connector.sendProgramInfo();
  }
}
