package tools.debugger;

import java.net.InetSocketAddress;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.Gson;

import som.VM;
import tools.debugger.message.Message.IncommingMessage;


public class WebSocketHandler extends WebSocketServer {
  private static final int NUM_THREADS = 1;

  private final FrontendConnector connector;
  private final Gson gson;

  WebSocketHandler(final InetSocketAddress address,
      final FrontendConnector connector, final Gson gson) {
    super(address, NUM_THREADS);
    this.connector = connector;
    this.gson = gson;
  }

  @Override
  public void onOpen(final WebSocket conn, final ClientHandshake handshake) { }

  @Override
  public void onClose(final WebSocket conn, final int code, final String reason,
      final boolean remote) {
    WebDebugger.log("onClose: code=" + code + " " + reason);
  }

  @Override
  public void onMessage(final WebSocket conn, final String message) {
    try {
      IncommingMessage respond = gson.fromJson(message, IncommingMessage.class);
      respond.process(connector, conn);
    } catch (Exception ex) {
      VM.errorPrint("Error while processing msg:" + message);
      ex.printStackTrace();
    }
  }

  @Override
  public void onError(final WebSocket conn, final Exception ex) {
    WebDebugger.log("error:");
    ex.printStackTrace();
  }
}
