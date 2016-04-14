package tools.debugger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.source.LineLocation;
import com.oracle.truffle.api.source.Source;

class WebSocketHandler extends WebSocketServer {
  private static final int NUM_THREADS = 1;

  private final CompletableFuture<WebSocket> clientConnected;

  WebSocketHandler(final InetSocketAddress address,
      final CompletableFuture<WebSocket> clientConnected) {
    super(address, NUM_THREADS);
    this.clientConnected = clientConnected;
  }

  @Override
  public void onOpen(final WebSocket conn, final ClientHandshake handshake) {
    clientConnected.complete(conn);
  }

  @Override
  public void onClose(final WebSocket conn, final int code, final String reason,
      final boolean remote) {
    WebDebugger.log("onClose: code=" + code + " " + reason);
  }

  @Override
  public void onMessage(final WebSocket conn, final String message) {
    JsonObject msg = Json.parse(message).asObject();

    switch (msg.getString("action", null)) {
      case "updateBreakpoint":
        WebDebugger.log("UPDATE BREAKPOINT");
        String sourceId   = msg.getString("sourceId", null);
        String sourceName = msg.getString("sourceName", null);
        int lineNumber    = msg.getInt("line", -1);
        boolean enabled   = msg.getBoolean("enabled", false);
        WebDebugger.log(sourceId + ":" + lineNumber + " " + enabled);

        Source source = JsonSerializer.getSource(sourceId);
        LineLocation line = source.createLineLocation(lineNumber);

        assert WebDebugger.truffleDebugger != null : "debugger has not be initialized yet";
        Breakpoint bp = WebDebugger.truffleDebugger.getBreakpoint(line);

        if (enabled && bp == null) {
          try {
            WebDebugger.log("SetLineBreakpoint line:" + line);
            Breakpoint newBp = WebDebugger.truffleDebugger.setLineBreakpoint(0, line, false);
            assert newBp != null;
          } catch (IOException e) {
            e.printStackTrace();
          }
        } else if (bp != null) {
          bp.setEnabled(enabled);
        }
        return;
      case "stepInto":
      case "stepOver":
      case "return":
      case "resume":
      case "stop": {
        String id = msg.getString("suspendEvent", null);
        SuspendedEvent event = WebDebugger.suspendEvents.get(id);
        assert event != null : "didn't find SuspendEvent";

        switch (msg.getString("action", null)) {
          case "stepInto": event.prepareStepInto(1); break;
          case "stepOver": event.prepareStepOver(1); break;
          case "return":   event.prepareStepOut();   break;
          case "resume":   event.prepareContinue();  break;
          case "stop":     event.prepareKill();      break;
        }
        WebDebugger.suspendFutures.get(id).complete(new Object());
        return;
      }
    }

    WebDebugger.log("not supported: onMessage: " + message);
  }

  @Override
  public void onError(final WebSocket conn, final Exception ex) {
    WebDebugger.log("error:");
    ex.printStackTrace();
  }
}
