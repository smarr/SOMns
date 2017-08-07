package tools.debugger;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.Gson;

import som.VM;
import tools.debugger.message.Message.IncommingMessage;


public abstract class WebSocketHandler extends WebSocketServer {
  private static final int NUM_THREADS = 1;

  private final CompletableFuture<Integer> connectionPort;

  WebSocketHandler(final int port) {
    super(new InetSocketAddress(port), NUM_THREADS);
    this.connectionPort = new CompletableFuture<>();
  }

  @Override
  public void onStart() {
    connectionPort.complete(getPort());
  }

  public int awaitStartup() throws ExecutionException {
    while (true) {
      try {
        return connectionPort.get();
      } catch (InterruptedException e) { /* Retry on interrupt. */ }
    }
  }

  @Override
  public void onOpen(final WebSocket conn, final ClientHandshake handshake) {}

  @Override
  public void onClose(final WebSocket conn, final int code, final String reason,
      final boolean remote) {
    WebDebugger.log("onClose: code=" + code + " " + reason);
  }

  @Override
  public void onError(final WebSocket conn, final Exception ex) {
    if (ex instanceof BindException) {
      connectionPort.completeExceptionally(ex);
      return;
    }
    WebDebugger.log("error:");
    ex.printStackTrace();
  }

  public static class MessageHandler extends WebSocketHandler {
    private final FrontendConnector connector;
    private final Gson              gson;

    public MessageHandler(final int port, final FrontendConnector connector,
        final Gson gson) {
      super(port);
      this.connector = connector;
      this.gson = gson;
    }

    @Override
    public void onMessage(final WebSocket conn, final String message) {
      try {
        IncommingMessage respond = gson.fromJson(message, IncommingMessage.class);
        respond.process(connector, conn);
      } catch (Throwable ex) {
        VM.errorPrint("Error while processing msg:" + message);
        ex.printStackTrace();
      }
    }
  }

  public static class TraceHandler extends WebSocketHandler {
    private final CompletableFuture<WebSocket> connection;

    public TraceHandler(final int port) {
      super(port);
      connection = new CompletableFuture<>();
    }

    @Override
    public void onOpen(final WebSocket conn, final ClientHandshake handshake) {
      connection.complete(conn);
    }

    @Override
    public void onMessage(final WebSocket conn, final String message) {}

    public CompletableFuture<WebSocket> getConnection() {
      return connection;
    }
  }
}
