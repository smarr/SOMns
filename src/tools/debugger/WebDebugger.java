package tools.debugger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.java_websocket.WebSocket;

import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.ExecutionEvent;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;
import com.sun.net.httpserver.HttpServer;

import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import tools.ObjectBuffer;
import tools.highlight.Tags;


/**
 * The WebDebugger connects the Truffle debugging facilities with a HTML5
 * application using WebSockets and JSON.
 */
@Registration(id = WebDebugger.ID)
public class WebDebugger extends TruffleInstrument {

  public static final String ID = "web-debugger";

  private HttpServer httpServer;
  private WebSocketHandler webSocketServer;
  private Future<WebSocket> clientConnected;

  private Instrumenter instrumenter;

  private final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> loadedSourcesTags = new HashMap<>();
  private final Map<Source, Set<RootNode>> rootNodes = new HashMap<>();

  private WebSocket client;
  private Debugger truffleDebugger;
  private Breakpoints breakpoints;

  public void reportSyntaxElement(final Class<? extends Tags> type,
      final SourceSection source) {
    Map<SourceSection, Set<Class<? extends Tags>>> sections = loadedSourcesTags.computeIfAbsent(
        source.getSource(), s -> new HashMap<>());
    Set<Class<? extends Tags>> tags = sections.computeIfAbsent(source, s -> new HashSet<>(2));
    tags.add(type);

    JsonSerializer.createSourceId(source.getSource());
    JsonSerializer.createSourceSectionId(source);
  }

  private final ArrayList<Source> notReady = new ArrayList<>();

  public void reportLoadedSource(final Source source) {
    if (webSocketServer == null || client == null) {
      notReady.add(source);
      return;
    }

    ensureConnectionIsAvailable();

    if (!notReady.isEmpty()) {
      for (Source s : notReady) {
        String json = JsonSerializer.createSourceAndSectionMessage(s, loadedSourcesTags.get(source));
        client.send(json);
      }
      notReady.clear();
    }

    String json = JsonSerializer.createSourceAndSectionMessage(source, loadedSourcesTags.get(source));
    client.send(json);
  }

  private void ensureConnectionIsAvailable() {
    assert webSocketServer != null;
    assert client != null;
    assert client.isOpen();
  }

  public void reportRootNodeAfterParsing(final RootNode rootNode) {
    assert rootNode.getSourceSection() != null : "RootNode without source section";
    Set<RootNode> roots = rootNodes.computeIfAbsent(
        rootNode.getSourceSection().getSource(), s -> new HashSet<>());
    assert !roots.contains(rootNode) : "This method was parsed twice? should not happen";
    roots.add(rootNode);
  }

  public void reportExecutionEvent(final ExecutionEvent e) {
    assert truffleDebugger == e.getDebugger();

    assert clientConnected != null;
    log("[DEBUGGER] Waiting for debugger to connect.");
    try {
      client = clientConnected.get();
    } catch (InterruptedException | ExecutionException ex) {
      // TODO Auto-generated catch block
      ex.printStackTrace();
    }
    log("[DEBUGGER] Debugger connected.");
  }

  private int nextSuspendEventId = 0;
  private final Map<String, SuspendedEvent> suspendEvents  = new HashMap<>();
  private final Map<String, CompletableFuture<Object>> suspendFutures = new HashMap<>();

  SuspendedEvent getSuspendedEvent(final String id) {
    return suspendEvents.get(id);
  }

  CompletableFuture<Object> getSuspendFuture(final String id) {
    return suspendFutures.get(id);
  }



  private String getNextSuspendEventId() {
    int id = nextSuspendEventId;
    nextSuspendEventId += 1;
    return "se-" + id;
  }

  public void reportSuspendedEvent(final SuspendedEvent e) {
    Node     suspendedNode = e.getNode();
    RootNode suspendedRoot = suspendedNode.getRootNode();
    Source suspendedSource;
    if (suspendedRoot.getSourceSection() != null) {
      suspendedSource = suspendedRoot.getSourceSection().getSource();
    } else {
      suspendedSource = suspendedNode.getSourceSection().getSource();
    }

    String id = getNextSuspendEventId();

    JSONObjectBuilder builder = JsonSerializer.createSuspendedEventJson(e,
        suspendedNode, suspendedRoot, suspendedSource, id,
        loadedSourcesTags, instrumenter, rootNodes);

    CompletableFuture<Object> future = new CompletableFuture<>();
    suspendEvents.put(id, e);
    suspendFutures.put(id, future);

    ensureConnectionIsAvailable();

    client.send(builder.toString());

    try {
      future.get();
    } catch (InterruptedException | ExecutionException e1) {
      log("[DEBUGGER] Future failed:");
      e1.printStackTrace();
    }
  }

  public void suspendExecution(final Node haltedNode,
      final MaterializedFrame haltedFrame) {
    SuspendedEvent event = truffleDebugger.createSuspendedEvent(haltedNode, haltedFrame);
    reportSuspendedEvent(event);
  }

  static void log(final String str) {
    // Checkstyle: stop
    System.out.println(str);
    // Checkstyle: resume
  }

  @Override
  protected void onDispose(final Env env) {
    ensureConnectionIsAvailable();

    log("[ACTORS] send message history");

    ObjectBuffer<ObjectBuffer<SFarReference>> actorsPerThread = Actor.getAllCreateActors();
    ObjectBuffer<ObjectBuffer<ObjectBuffer<EventualMessage>>> messagesPerThread = Actor.getAllProcessedMessages();

    Map<SFarReference, String> actorsToIds = createActorMap(actorsPerThread);
    Map<Actor, String> actorObjsToIds = new HashMap<>(actorsToIds.size());
    for (Entry<SFarReference, String> e : actorsToIds.entrySet()) {
      Actor a = e.getKey().getActor();
      assert !actorObjsToIds.containsKey(a);
      actorObjsToIds.put(a, e.getValue());
    }

    JSONObjectBuilder msg = JsonSerializer.createMessageHistoryJson(messagesPerThread,
        actorsToIds, actorObjsToIds);

    String m = msg.toString();
    log("[ACTORS] Message length: " + m.length());
    client.send(m);
    log("[ACTORS] Message sent?");
    try {
      Thread.sleep(150000);
    } catch (InterruptedException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    log("[ACTORS] Message sent waiting completed");

//    log("[ACTORS] " + msg.toString());
    client.close();
  }

  private static Map<SFarReference, String> createActorMap(
      final ObjectBuffer<ObjectBuffer<SFarReference>> actorsPerThread) {
    HashMap<SFarReference, String> map = new HashMap<>();
    int numActors = 0;

    for (ObjectBuffer<SFarReference> perThread : actorsPerThread) {
      for (SFarReference a : perThread) {
        assert !map.containsKey(a);
        map.put(a, "a-" + numActors);
        numActors += 1;
      }
    }
    return map;
  }

  @Override
  protected void onCreate(final Env env) {
    instrumenter = env.getInstrumenter();
    env.registerService(this);
  }

  public void startServer(final Debugger dbg) {
    truffleDebugger = dbg;
    breakpoints = new Breakpoints(dbg);

    try {
      log("[DEBUGGER] Initialize HTTP and WebSocket Server for Debugger");
      int port = 8889;
      initializeWebSocket(8889);
      log("[DEBUGGER] Started WebSocket Server");

      port = 8888;
      initializeHttpServer(port);
      log("[DEBUGGER] Started HTTP Server");
      log("[DEBUGGER]   URL: http://localhost:" + port + "/index.html");
    } catch (IOException e) {
      e.printStackTrace();
      log("Failed starting WebSocket and/or HTTP Server");
    }
    // now we continue execution, but we wait for the future in the execution
    // event
  }

  private void initializeHttpServer(final int port) throws IOException {
    if (httpServer == null) {
      InetSocketAddress address = new InetSocketAddress(port);
      httpServer = HttpServer.create(address, 0);
      httpServer.createContext("/", new WebResourceHandler());
      httpServer.setExecutor(null);
      httpServer.start();
    }
  }

  private void initializeWebSocket(final int port) {
    if (webSocketServer == null) {
      clientConnected = new CompletableFuture<WebSocket>();
      InetSocketAddress addess = new InetSocketAddress(port);
      webSocketServer = new WebSocketHandler(
          addess, (CompletableFuture<WebSocket>) clientConnected,
          breakpoints, this);
      webSocketServer.start();
    }
  }
}
