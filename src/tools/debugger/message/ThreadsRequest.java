package tools.debugger.message;

import org.java_websocket.WebSocket;

import tools.debugger.FrontendConnector;
import tools.debugger.message.Message.Request;


public class ThreadsRequest extends Request {

  ThreadsRequest(final int requestId) {
    super(requestId);
  }

  @Override
  public void process(final FrontendConnector connector, final WebSocket conn) {
    connector.sendThreads(requestId);
  }
}
