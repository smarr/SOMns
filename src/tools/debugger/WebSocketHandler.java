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

import gson.ClassHierarchyAdapterFactory;
import som.VM;
import tools.debugger.message.InitialBreakpointsResponds;
import tools.debugger.message.Respond;
import tools.debugger.message.UpdateBreakpoint;
import tools.debugger.session.AsyncMessageReceiveBreakpoint;
import tools.debugger.session.BreakpointInfo;
import tools.debugger.session.LineBreakpoint;
import tools.debugger.session.MessageReceiveBreakpoint;
import tools.debugger.session.MessageSenderBreakpoint;


public class WebSocketHandler extends WebSocketServer {
  private static final int NUM_THREADS = 1;

  private final CompletableFuture<WebSocket> clientConnected;
  private final FrontendConnector connector;
  private final Gson gson;

  WebSocketHandler(final InetSocketAddress address,
      final CompletableFuture<WebSocket> clientConnected,
      final FrontendConnector connector) {
    super(address, NUM_THREADS);
    this.clientConnected = clientConnected;
    this.connector = connector;
    this.gson = createJsonProcessor();
  }

  // TODO: to be removed
  private static final String INITIAL_BREAKPOINTS = "initialBreakpoints";
  private static final String UPDATE_BREAKPOINT   = "updateBreakpoint";

  public static Gson createJsonProcessor() {
    ClassHierarchyAdapterFactory<Respond> respondAF = new ClassHierarchyAdapterFactory<>(Respond.class, "action");
    respondAF.register(INITIAL_BREAKPOINTS, InitialBreakpointsResponds.class);
    respondAF.register(UPDATE_BREAKPOINT,   UpdateBreakpoint.class);

    ClassHierarchyAdapterFactory<BreakpointInfo> breakpointAF = new ClassHierarchyAdapterFactory<>(BreakpointInfo.class, "type");
    breakpointAF.register(LineBreakpoint.class);
    breakpointAF.register(MessageSenderBreakpoint.class);
    breakpointAF.register(MessageReceiveBreakpoint.class);
    breakpointAF.register(AsyncMessageReceiveBreakpoint.class);

    return new GsonBuilder().
        registerTypeAdapterFactory(respondAF).
        registerTypeAdapterFactory(breakpointAF).
        create();
  }

  @Override
  public void onOpen(final WebSocket conn, final ClientHandshake handshake) { }

  @Override
  public void onClose(final WebSocket conn, final int code, final String reason,
      final boolean remote) {
    WebDebugger.log("onClose: code=" + code + " " + reason);
  }

  @Deprecated // should be moved to proper separate class, perhaps frontend?
  public void onInitialBreakpoints(final WebSocket conn, final BreakpointInfo[] breakpoints) {
    for (BreakpointInfo bp : breakpoints) {
      bp.registerOrUpdate(connector);

    }
    clientConnected.complete(conn);
  }

  @Deprecated // should be moved to proper separate class
  public void onBreakpointUpdate(final BreakpointInfo breakpoint) {
    breakpoint.registerOrUpdate(connector);
  }

  @Deprecated
  public SuspendedEvent getSuspendedEvent(final String id) {
    return connector.getSuspendedEvent(id);
  }

  @Deprecated
  public void completeSuspendFuture(final String id) {
    connector.completeSuspendFuture(id, new Object());
  }

  @Override
  public void onMessage(final WebSocket conn, final String message) {
    try {
      Respond respond = gson.fromJson(message, Respond.class);
      respond.process(this, conn);
    } catch (Throwable t) {
      VM.errorPrint("Error on parsing Json:" + message);
      t.printStackTrace();
    }
  }

  @Override
  public void onError(final WebSocket conn, final Exception ex) {
    WebDebugger.log("error:");
    ex.printStackTrace();
  }
}
