package tools.debugger;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.oracle.truffle.api.debug.SuspendedEvent;

import som.interpreter.actors.Actor.Role;

class WebSocketHandler extends WebSocketServer {
  private static final int NUM_THREADS = 1;

  private final CompletableFuture<WebSocket> clientConnected;
  private final FrontendConnector connector;

  WebSocketHandler(final InetSocketAddress address,
      final CompletableFuture<WebSocket> clientConnected,
      final FrontendConnector connector) {
    super(address, NUM_THREADS);
    this.clientConnected = clientConnected;
    this.connector = connector;
  }

  @Override
  public void onOpen(final WebSocket conn, final ClientHandshake handshake) { }

  @Override
  public void onClose(final WebSocket conn, final int code, final String reason,
      final boolean remote) {
    WebDebugger.log("onClose: code=" + code + " " + reason);
  }

  private void processBreakpoint(final JsonObject obj) {
    String type = obj.getString("type", null);
    URI uri = null;
    try {
      uri = new URI(obj.getString("sourceUri", null));
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }

    boolean enabled = obj.getBoolean("enabled", false);
    String role = obj.getString("role", null);
    Role selectedRole = null;

    if (role != null) {
      switch (role) {
        case "receiver":
          selectedRole = Role.RECEIVER;
          break;
        case "sender":
          selectedRole = Role.SENDER;
          break;
      }
    }

    switch (type) {
      case "lineBreakpoint":
        processLineBreakpoint(obj, uri, enabled);
        break;
      case "sendBreakpoint":
        processSendBreakpoint(obj, uri, enabled, selectedRole);
        break;
    }
  }

  private void processSendBreakpoint(final JsonObject obj, final URI sourceUri,
      final boolean enabled, final Role role) {
    int startLine   = obj.getInt("startLine",   -1);
    int startColumn = obj.getInt("startColumn", -1);
    int charLength  = obj.getInt("charLength",  -1);

    connector.requestBreakpoint(enabled, sourceUri, startLine, startColumn, charLength, role);
  }

  private void processLineBreakpoint(final JsonObject obj, final URI sourceUri,
      final boolean enabled) {
    int lineNumber = obj.getInt("line", -1);
    connector.requestBreakpoint(enabled, sourceUri, lineNumber);
  }

  @Override
  public void onMessage(final WebSocket conn, final String message) {
    JsonObject msg = Json.parse(message).asObject();

    switch (msg.getString("action", null)) {
      case "initialBreakpoints":
        JsonArray bps = msg.get("breakpoints").asArray();
        for (JsonValue bp : bps) {
          processBreakpoint(bp.asObject());
        }
        clientConnected.complete(conn);
        return;

      case "updateBreakpoint":
        WebDebugger.log("UPDATE BREAKPOINT");
        processBreakpoint(msg.get("breakpoint").asObject());
        return;
      case "stepInto":
      case "stepOver":
      case "return":
      case "resume":
      case "stop": {
        WebDebugger.log("STOP");
        String id = msg.getString("suspendEvent", null);
        SuspendedEvent event = connector.getSuspendedEvent(id);
        assert event != null : "didn't find SuspendEvent";

        switch (msg.getString("action", null)) {
          case "stepInto": event.prepareStepInto(1); break;
          case "stepOver": event.prepareStepOver(1); break;
          case "return":   event.prepareStepOut();   break;
          case "resume":   event.prepareContinue();  break;
          case "stop":     event.prepareKill();      break;
        }
        connector.completeSuspendFuture(id, new Object());
        return;
      }

      // TODO: add case of action pause
    }

    WebDebugger.log("not supported: onMessage: " + message);
  }

  @Override
  public void onError(final WebSocket conn, final Exception ex) {
    WebDebugger.log("error:");
    ex.printStackTrace();
  }
}
