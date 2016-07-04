package tools.debugger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.source.LineLocation;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

class WebSocketHandler extends WebSocketServer {
  private static final int NUM_THREADS = 1;

  private final CompletableFuture<WebSocket> clientConnected;

  WebSocketHandler(final InetSocketAddress address,
      final CompletableFuture<WebSocket> clientConnected) {
    super(address, NUM_THREADS);
    this.clientConnected = clientConnected;
  }

  @Override
  public void onOpen(final WebSocket conn, final ClientHandshake handshake) { }

  @Override
  public void onClose(final WebSocket conn, final int code, final String reason,
      final boolean remote) {
    WebDebugger.log("onClose: code=" + code + " " + reason);
  }

  private void processBreakpoint(final JsonObject obj) {
    String type =  obj.getString("type", null);
    String sourceId   = obj.getString("sourceId", null);
    String sourceName = obj.getString("sourceName", null);
    Source source = getSource(sourceId, sourceName);
    boolean enabled   = obj.getBoolean("enabled", false);

    assert WebDebugger.truffleDebugger != null : "debugger has not be initialized yet";

    switch (type) {
      case "lineBreakpoint":
        processLineBreakpoint(obj, source, enabled);
        break;
      case "sendBreakpoint":
        processSendBreakpoint(obj, source, enabled);
        break;
    }
  }

  private void processSendBreakpoint(final JsonObject obj, final Source source,
      final boolean enabled) {
    String sourceSectionId = obj.getString("sectionId", null);
    SourceSection section = JsonSerializer.getSourceSection(sourceSectionId);

    Breakpoint bp = WebDebugger.truffleDebugger.getBreakpoint(section);

    if (enabled && bp == null) {
      try {
        WebDebugger.log("SetSectionBreakpoint line: " + sourceSectionId);
        Breakpoint newBp = WebDebugger.truffleDebugger.setSourceSectionBreakpoint(0, section, false);
        assert newBp != null;
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else if (bp != null) {
      bp.setEnabled(enabled);
    }

  }

  private void processLineBreakpoint(final JsonObject obj, final Source source,
      final boolean enabled) {
    int lineNumber = obj.getInt("line", -1);

    LineLocation line = source.createLineLocation(lineNumber);
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
  }

  private Source getSource(final String sourceId, final String sourceName) {
    Source source = JsonSerializer.getSource(sourceId);
    if (source == null) {
      // this can happen when restoring breakpoints on load and sources are
      // not yet loaded
      source = Source.find(sourceName);
    } else {
      assert source.getName().equals(sourceName) :
        "Filenames of source by id and known name should match, probably should handle this case";
    }
    return source;
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
