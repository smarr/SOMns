package tools.debugger;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class BinaryWebSocketHandler extends WebSocketServer{
  private static final int NUM_THREADS = 1;
  private final CompletableFuture<WebSocket> connection;

  public BinaryWebSocketHandler(final InetSocketAddress address) {
    super(address, NUM_THREADS);
    connection = new CompletableFuture<>();
  }

  @Override
  public void onClose(final WebSocket conn, final int arg1, final String arg2, final boolean arg3) {
    // TODO Auto-generated method stub

  }

  @Override
  public void onError(final WebSocket conn, final Exception ex) {
    WebDebugger.log("error:");
    ex.printStackTrace();
  }

  @Override
  public void onMessage(final WebSocket conn, final String msg) {
    // TODO Auto-generated method stub
  }

  @Override
  public void onOpen(final WebSocket conn, final ClientHandshake handshake) {
    connection.complete(conn);
  }

  public CompletableFuture<WebSocket> getConnection() {
    return connection;
  }
}
