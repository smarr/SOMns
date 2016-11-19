package tools.debugger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.java_websocket.WebSocket;

import com.google.gson.Gson;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.sun.net.httpserver.HttpServer;

import som.VmSettings;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import tools.ObjectBuffer;
import tools.SourceCoordinate;
import tools.SourceCoordinate.TaggedSourceCoordinate;
import tools.Tagging;
import tools.actors.ActorExecutionTrace;
import tools.debugger.WebDebugger.Suspension;
import tools.debugger.message.Message;
import tools.debugger.message.MessageHistory;
import tools.debugger.message.SourceMessage;
import tools.debugger.message.SourceMessage.SourceData;
import tools.debugger.message.StoppedMessage;
import tools.debugger.message.SuspendedEventMessage;
import tools.debugger.session.AsyncMessageReceiveBreakpoint;
import tools.debugger.session.Breakpoints;
import tools.debugger.session.LineBreakpoint;
import tools.debugger.session.MessageReceiveBreakpoint;
import tools.debugger.session.MessageSenderBreakpoint;

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
  private final WebSocketHandler receiver;

  /**
   * Sends requests to the client.
   */
  private WebSocket sender;

  /**
   * Future to await the client's connection.
   */
  private CompletableFuture<WebSocket> clientConnected;

  private final Gson gson;

  private final ArrayList<Source> notReady = new ArrayList<>(); //TODO rename: toBeSend

  private int numActors = 0;

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
      int port = 7977;
      receiver = initializeWebSocket(port, clientConnected);
      log("[DEBUGGER] Started WebSocket Server");

      port = 8888;
      contentServer = initializeHttpServer(port);
      log("[DEBUGGER] Started HTTP Server");
      log("[DEBUGGER]   URL: http://localhost:" + port + "/index.html");
    } catch (IOException e) {
      log("Failed starting WebSocket and/or HTTP Server");
      throw new RuntimeException(e);
    }
    // now we continue execution, but we wait for the future in the execution
    // event
  }

  private WebSocketHandler initializeWebSocket(final int port,
      final Future<WebSocket> clientConnected) {
    InetSocketAddress address = new InetSocketAddress(port);
    WebSocketHandler server = new WebSocketHandler(address, this, gson);
    server.start();
    return server;
  }

  private HttpServer initializeHttpServer(final int port) throws IOException {
    InetSocketAddress address = new InetSocketAddress(port);
    HttpServer httpServer = HttpServer.create(address, 0);
    httpServer.createContext("/", new WebResourceHandler());
    httpServer.setExecutor(null);
    httpServer.start();
    return httpServer;
  }

  private void ensureConnectionIsAvailable() {
    assert receiver != null;
    assert sender != null;
    assert sender.isOpen();
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
    SourceData[] sources = new SourceData[1];
    sources[0] = new SourceData(source.getCode(), source.getMimeType(),
        source.getName(), source.getURI().toString(),
        createSourceSections(source, loadedSourcesTags, instrumenter, rootNodes),
        SourceMessage.createMethodDefinitions(rootNodes));
    send(new SourceMessage(sources));
  }

  private void send(final Message msg) {
    ensureConnectionIsAvailable();
    sender.send(gson.toJson(msg, Message.class));
  }

  private void sendBufferedSources(
      final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> loadedSourcesTags,
      final Map<Source, Set<RootNode>> rootNodes) {
    if (!notReady.isEmpty()) {
      for (Source s : notReady) {
        sendSource(s, loadedSourcesTags, rootNodes.get(s));
      }
      notReady.clear();
    }
  }

  public void sendLoadedSource(final Source source,
      final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> loadedSourcesTags,
      final Map<Source, Set<RootNode>> rootNodes) {
    if (receiver == null || sender == null) {
      notReady.add(source);
      return;
    }

    ensureConnectionIsAvailable();
    sendBufferedSources(loadedSourcesTags, rootNodes);
    sendSource(source, loadedSourcesTags, rootNodes.get(source));
  }

  public void awaitClient() {
    assert clientConnected != null;
    log("[DEBUGGER] Waiting for debugger to connect.");
    try {
      sender = clientConnected.get();
    } catch (InterruptedException | ExecutionException ex) {
      throw new RuntimeException(ex);
    }
    log("[DEBUGGER] Debugger connected.");
  }

  /**
   * will be removed, required to send actor information incremental.
   */
  private Map<SFarReference, String> createNewActorsMap(
      final ObjectBuffer<ObjectBuffer<SFarReference>> actorsPerThread) {
    HashMap<SFarReference, String> map = new HashMap<>();

    for (ObjectBuffer<SFarReference> perThread : actorsPerThread) {
      Iterator<SFarReference> iter = perThread.iteratorFromMemory();
      perThread.memorize();

      while (iter.hasNext()) {
        SFarReference a = iter.next();
        map.put(a, "a-" + numActors);
        numActors += 1;
      }
    }
    return map;
  }

  /**
   * will be removed, required to map message senders/receivers to ids.
   */
  private static Map<Actor, String> createActorIdMap(
      final ObjectBuffer<ObjectBuffer<SFarReference>> actorsPerThread) {
    HashMap<Actor, String> map = new HashMap<>();
    int numActors = 0;
    for (ObjectBuffer<SFarReference> perThread : actorsPerThread) {
      for (SFarReference a : perThread) {
        assert !map.containsKey(a);
        map.put(a.getActor(), "a-" + numActors);
        numActors += 1;
      }

    }
    return map;
  }

  public void sendSuspendedEvent(final Suspension suspension) {
    sendTracingData();
    // TODO: I need to capture the stack here, to make sure it is accessible
    //       also when running on Graal

    send(SuspendedEventMessage.create(
        suspension.getEvent(),
        SUSPENDED_EVENT_ID_PREFIX + suspension.activityId));
  }

  private static final String SUSPENDED_EVENT_ID_PREFIX = "se-";

  public void sendStoppedMessage(final Suspension suspension) {
    send(StoppedMessage.create(suspension));
  }

  public void sendTracingData() {
    if (!VmSettings.ACTOR_TRACING) {
      return;
    }

    ObjectBuffer<ObjectBuffer<SFarReference>> actorsPerThread = ActorExecutionTrace.getAllCreateActors();
    ObjectBuffer<ObjectBuffer<ObjectBuffer<EventualMessage>>> messagesPerThread = ActorExecutionTrace.getAllProcessedMessages();

    Map<SFarReference, String> newActors = createNewActorsMap(actorsPerThread);
    Map<Actor, String> completeActorIdMap = createActorIdMap(actorsPerThread);


    MessageHistory msg = MessageHistory.create(
        newActors, messagesPerThread, completeActorIdMap);

    String m = gson.toJson(msg, Message.class);
    log("[ACTORS] Message length: " + m.length());
    sender.send(m);

    ActorExecutionTrace.clearProcessedMessages();
  }

  public void sendActorHistory() {
    if (!VmSettings.ACTOR_TRACING) {
      return;
    }

    ensureConnectionIsAvailable();

    log("[ACTORS] send message history");

    sendTracingData();
    log("[ACTORS] Message sent?");
    try {
      Thread.sleep(150000);
    } catch (InterruptedException e1) { }
    log("[ACTORS] Message sent waiting completed");

    sender.close();
  }

  public void registerOrUpdate(final LineBreakpoint bp) {
    breakpoints.addOrUpdate(bp);
  }

  public void registerOrUpdate(final MessageSenderBreakpoint bp) {
    breakpoints.addOrUpdate(bp);
  }

  public void registerOrUpdate(final MessageReceiveBreakpoint bp) {
    breakpoints.addOrUpdate(bp);
  }

  public void registerOrUpdate(final AsyncMessageReceiveBreakpoint bp) {
    breakpoints.addOrUpdate(bp);
  }

  public SuspendedEvent getSuspendedEvent(final String id) {
    int activityId = Integer.valueOf(id.substring(SUSPENDED_EVENT_ID_PREFIX.length()));
    return webDebugger.getSuspendedEvent(activityId);
  }

  public void completeSuspendFuture(final String id, final Object value) {
    int activityId = Integer.valueOf(id.substring(SUSPENDED_EVENT_ID_PREFIX.length()));
    webDebugger.getSuspendFuture(activityId).complete(value);
  }

  static void log(final String str) {
    // Checkstyle: stop
    System.out.println(str);
    // Checkstyle: resume
  }

  public void completeConnection(final WebSocket conn, final boolean debuggerProtocol) {
    clientConnected.complete(conn);
    webDebugger.useDebuggerProtocol(debuggerProtocol);
  }

  public void shutdown() {
    int delaySec = 5;
    contentServer.stop(delaySec);

    sender.close();
    try {
      int delayMsec = 1000;
      receiver.stop(delayMsec);
    } catch (InterruptedException e) { }
  }
}
