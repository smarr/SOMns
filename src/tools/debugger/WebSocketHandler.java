package tools.debugger;

import java.net.InetSocketAddress;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import gson.ClassHierarchyAdapterFactory;
import som.VM;
import tools.debugger.message.InitialBreakpointsResponds;
import tools.debugger.message.Respond;
import tools.debugger.message.StepMessage.Resume;
import tools.debugger.message.StepMessage.Return;
import tools.debugger.message.StepMessage.StepInto;
import tools.debugger.message.StepMessage.StepOver;
import tools.debugger.message.StepMessage.Stop;
import tools.debugger.message.UpdateBreakpoint;
import tools.debugger.session.AsyncMessageReceiveBreakpoint;
import tools.debugger.session.BreakpointInfo;
import tools.debugger.session.LineBreakpoint;
import tools.debugger.session.MessageReceiveBreakpoint;
import tools.debugger.session.MessageSenderBreakpoint;


public class WebSocketHandler extends WebSocketServer {
  private static final int NUM_THREADS = 1;

  private final FrontendConnector connector;
  private final Gson gson;

  WebSocketHandler(final InetSocketAddress address,
      final FrontendConnector connector) {
    super(address, NUM_THREADS);
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
    respondAF.register("stepInto", StepInto.class);
    respondAF.register("stepOver", StepOver.class);
    respondAF.register("return",   Return.class);
    respondAF.register("resume",   Resume.class);
    respondAF.register("stop",     Stop.class);

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

  @Override
  public void onMessage(final WebSocket conn, final String message) {
    try {
      Respond respond = gson.fromJson(message, Respond.class);
      respond.process(connector, conn);
    } catch (Exception ex) {
      VM.errorPrint("Error on parsing Json:" + message);
      ex.printStackTrace();
    }
  }

  @Override
  public void onError(final WebSocket conn, final Exception ex) {
    WebDebugger.log("error:");
    ex.printStackTrace();
  }
}
