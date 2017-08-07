package tools.debugger.message;

import org.java_websocket.WebSocket;

import tools.debugger.FrontendConnector;


public abstract class Message {
  public abstract static class IncommingMessage extends Message {
    public abstract void process(FrontendConnector connector, WebSocket conn);
  }

  /**
   * With id to connect request and response.
   */
  public abstract static class Request extends IncommingMessage {
    protected int requestId;

    public Request(final int requestId) {
      this.requestId = requestId;
    }
  }

  public static class OutgoingMessage extends Message {}

  public abstract static class Response extends OutgoingMessage {
    protected int requestId;

    public Response(final int requestId) {
      this.requestId = requestId;
    }
  }
}
