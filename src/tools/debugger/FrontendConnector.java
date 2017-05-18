package tools.debugger;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import org.java_websocket.WebSocket;

import com.google.gson.Gson;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.sun.net.httpserver.HttpServer;

import som.vm.VmSettings;
import som.vmobjects.SSymbol;
import tools.SourceCoordinate;
import tools.SourceCoordinate.TaggedSourceCoordinate;
import tools.Tagging;
import tools.TraceData;
import tools.concurrency.ActorExecutionTrace;
import tools.debugger.WebSocketHandler.MessageHandler;
import tools.debugger.WebSocketHandler.TraceHandler;
import tools.debugger.entities.ActivityType;
import tools.debugger.entities.BreakpointType;
import tools.debugger.entities.DynamicScopeType;
import tools.debugger.entities.EntityType;
import tools.debugger.entities.Implementation;
import tools.debugger.entities.PassiveEntityType;
import tools.debugger.entities.ReceiveOp;
import tools.debugger.entities.SendOp;
import tools.debugger.entities.SteppingType;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.InitializationResponse;
import tools.debugger.message.Message;
import tools.debugger.message.Message.OutgoingMessage;
import tools.debugger.message.ProgramInfoResponse;
import tools.debugger.message.ScopesResponse;
import tools.debugger.message.SourceMessage;
import tools.debugger.message.SourceMessage.SourceData;
import tools.debugger.message.StackTraceResponse;
import tools.debugger.message.StoppedMessage;
import tools.debugger.message.SymbolMessage;
import tools.debugger.message.VariablesResponse;
import tools.debugger.session.Breakpoints;
import tools.debugger.session.LineBreakpoint;

/**
 * Connect the debugger to the UI front-end.
 */
public class FrontendConnector {

  private Instrumenter instrumenter;

  private final Breakpoints breakpoints;
  private final WebDebugger webDebugger;

  /**
   * Serves the static resources.
   */
  private final HttpServer contentServer;

  /**
   * Receives requests from the client.
   */
  private final MessageHandler messageHandler;
  private final TraceHandler   traceHandler;

  /**
   * Sends requests to the client.
   */
  private WebSocket messageSocket;

  private WebSocket traceSocket;

  /**
   * Future to await the client's connection.
   */
  private CompletableFuture<WebSocket> clientConnected;

  private final Gson gson;
  private static final int MESSAGE_PORT = 7977;
  private static final int TRACE_PORT   = 7978;
  private static final int HTTP_PORT    = 8888;
  private static final int EPHEMERAL_PORT = 0;

  private final ArrayList<Source> sourceToBeSent = new ArrayList<>();

  public FrontendConnector(final Breakpoints breakpoints,
      final Instrumenter instrumenter, final WebDebugger webDebugger,
      final Gson gson) {
    this.instrumenter = instrumenter;
    this.breakpoints = breakpoints;
    this.webDebugger = webDebugger;
    this.gson = gson;

    clientConnected = new CompletableFuture<WebSocket>();

    try {
      log("[DEBUGGER] Initialize HTTP and WebSocket Server for Debugger");
      messageHandler = initializeWebSocket(MESSAGE_PORT, port -> new MessageHandler(port, this, gson));
      traceHandler = initializeWebSocket(TRACE_PORT, port -> new TraceHandler(port));
      log("[DEBUGGER] Started WebSocket Servers");
      log("[DEBUGGER]   Message Handler: " + messageHandler.getPort());
      log("[DEBUGGER]   Trace Handler:   " + traceHandler.getPort());

      contentServer = initializeHttpServer(HTTP_PORT,
          messageHandler.getPort(), traceHandler.getPort());
      log("[DEBUGGER] Started HTTP Server");
      log("[DEBUGGER]   URL: http://localhost:" + contentServer.getAddress().getPort() + "/index.html");
    } catch (IOException e) {
      log("Failed starting WebSocket and/or HTTP Server");
      throw new RuntimeException(e);
    }
    // now we continue execution, but we wait for the future in the execution
    // event
  }

  public Breakpoints getBreakpoints() {
    return breakpoints;
  }

  private <T extends WebSocketHandler> T tryInitializingWebSocket(final T server) throws Throwable {
    server.start();
    try {
      server.awaitStartup();
    } catch (ExecutionException e) {
      throw e.getCause();
    }
    return server;
  }

  private <T extends WebSocketHandler> T initializeWebSocket(final int port, final Function<Integer, T> ctor) {
    try {
      return tryInitializingWebSocket(ctor.apply(port));
    } catch (BindException e) {
      try {
        return tryInitializingWebSocket(ctor.apply(EPHEMERAL_PORT));
      } catch (Throwable e1) {
        throw new RuntimeException(e);
      }
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  private HttpServer tryInitializingHttpServer(final int port,
      final int debuggerPort, final int tracePort) throws IOException {
    InetSocketAddress address = new InetSocketAddress(port);
    HttpServer httpServer = HttpServer.create(address, 0);
    httpServer.createContext("/", new WebResourceHandler(debuggerPort, tracePort));
    httpServer.setExecutor(null);
    httpServer.start();
    return httpServer;
  }

  private HttpServer initializeHttpServer(final int port,
      final int debuggerPort, final int tracePort) throws IOException {
    try {
      return tryInitializingHttpServer(port, debuggerPort, tracePort);
    } catch (BindException e) {
      return tryInitializingHttpServer(EPHEMERAL_PORT, debuggerPort, tracePort);
    }
  }

  private void ensureConnectionIsAvailable() {
    assert messageHandler != null;
    assert messageSocket != null;
    assert messageSocket.isOpen();
  }

  // TODO: simplify, way to convoluted
  private static TaggedSourceCoordinate[] createSourceSections(final Source source,
      final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> sourcesTags,
      final Instrumenter instrumenter, final Set<RootNode> rootNodes) {
    Set<SourceSection> sections = new HashSet<>();
    Map<SourceSection, Set<Class<? extends Tags>>> tagsForSections = sourcesTags.get(source);

    if (tagsForSections != null) {
      Tagging.collectSourceSectionsAndTags(rootNodes, tagsForSections, instrumenter);
      for (SourceSection section : tagsForSections.keySet()) {
        if (section.getSource() == source) {
          sections.add(section);
        }
      }
    }

    TaggedSourceCoordinate[] result = new TaggedSourceCoordinate[sections.size()];
    int i = 0;
    for (SourceSection section : sections) {
      result[i] = SourceCoordinate.create(section, tagsForSections.get(section));
      i += 1;
    }

    return result;
  }

  private void sendSource(final Source source,
      final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> loadedSourcesTags,
      final Set<RootNode> rootNodes) {
    SourceData data = new SourceData(source.getCode(), source.getMimeType(),
        source.getName(), source.getURI().toString(),
        createSourceSections(source, loadedSourcesTags, instrumenter, rootNodes),
        SourceMessage.createMethodDefinitions(rootNodes));
    send(new SourceMessage(data));
  }

  private void send(final Message msg) {
    ensureConnectionIsAvailable();
    messageSocket.send(gson.toJson(msg, OutgoingMessage.class));
  }

  private void sendBufferedSources(
      final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> loadedSourcesTags,
      final Map<Source, Set<RootNode>> rootNodes) {
    if (!sourceToBeSent.isEmpty()) {
      for (Source s : sourceToBeSent) {
        sendSource(s, loadedSourcesTags, rootNodes.get(s));
      }
      sourceToBeSent.clear();
    }
  }

  public void sendLoadedSource(final Source source,
      final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> loadedSourcesTags,
      final Map<Source, Set<RootNode>> rootNodes) {
    if (messageHandler == null || messageSocket == null) {
      sourceToBeSent.add(source);
      return;
    }

    ensureConnectionIsAvailable();
    sendBufferedSources(loadedSourcesTags, rootNodes);
    sendSource(source, loadedSourcesTags, rootNodes.get(source));
  }

  public void sendSymbols(final ArrayList<SSymbol> symbolsToWrite) {
    send(new SymbolMessage(symbolsToWrite));
  }

  public void sendTracingData(final ByteBuffer b) {
    traceSocket.send(b);
  }

  public void awaitClient() {
    assert VmSettings.ACTOR_TRACING && VmSettings.TRUFFLE_DEBUGGER_ENABLED;
    assert clientConnected != null;
    assert messageSocket == null && traceSocket == null;
    assert traceHandler.getConnection() != null;

    log("[DEBUGGER] Waiting for debugger to connect.");
    try {
      messageSocket = clientConnected.get();
      traceSocket = traceHandler.getConnection().get();
    } catch (InterruptedException | ExecutionException ex) {
      throw new RuntimeException(ex);
    }
    ActorExecutionTrace.setFrontEnd(this);
    log("[DEBUGGER] Debugger connected.");
  }

  public void sendStackTrace(final int startFrame, final int levels,
      final Suspension suspension, final int requestId) {
    send(StackTraceResponse.create(startFrame, levels, suspension, requestId));
  }

  public void sendScopes(final long frameId, final Suspension suspension,
      final int requestId) {
    send(ScopesResponse.create(frameId, suspension, requestId));
  }

  public void sendVariables(final long varRef, final int requestId, final Suspension suspension) {
    send(VariablesResponse.create(varRef, requestId, suspension));
  }

  public void sendStoppedMessage(final Suspension suspension) {
    send(StoppedMessage.create(suspension));
  }

  public void sendTracingData() {
    if (VmSettings.ACTOR_TRACING) {
      ActorExecutionTrace.forceSwapBuffers();
    }
  }

  public void sendProgramInfo() {
    send(ProgramInfoResponse.create(webDebugger.vm.getArguments()));
  }

  public void registerOrUpdate(final LineBreakpoint bp) {
    breakpoints.addOrUpdate(bp);
  }

  public Suspension getSuspension(final long activityId) {
    return webDebugger.getSuspension(activityId);
  }

  public Suspension getSuspensionForGlobalId(final long globalId) {
    return webDebugger.getSuspension(TraceData.getActivityIdFromGlobalValId(globalId));
  }

  static void log(final String str) {
    // Checkstyle: stop
    System.out.println(str);
    // Checkstyle: resume
  }

  public void completeConnection(final WebSocket conn) {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> closeAllSockets()));

    clientConnected.complete(conn);
    send(InitializationResponse.create(EntityType.values(),
        ActivityType.values(), PassiveEntityType.values(),
        DynamicScopeType.values(), SendOp.values(), ReceiveOp.values(),
        BreakpointType.values(), SteppingType.values(), Implementation.values()));
  }

  private void closeAllSockets() {
    final int delay = 0;
    contentServer.stop(delay);

    messageSocket.close();
    if (traceSocket != null) {
      traceSocket.close();
    }
    try {
      messageHandler.stop(delay);
      traceHandler.stop(delay);
    } catch (InterruptedException e) { }
  }
}
