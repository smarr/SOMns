package tools.debugger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.ExecutionEvent;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.LineLocation;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONArrayBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import som.vm.NotYetImplementedException;
import tools.ObjectBuffer;
import tools.Tagging;
import tools.highlight.JsonWriter;
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

  private static Instrumenter instrumenter;

  private static final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> loadedSourcesTags = new HashMap<>();

  private static int nextSourceId = 0;
  private static int nextSourceSectionId = 0;

  private static final Map<Source, String> sourcesId = new HashMap<>();
  private static final Map<String, Source> idSources = new HashMap<>();
  private static final Map<SourceSection, String> sourceSectionId = new HashMap<>();
  private static final Map<Source, Set<RootNode>> rootNodes = new HashMap<>();

  private static WebDebugger debugger;
  private static WebSocket client;
  private static Debugger truffleDebugger;

  public WebDebugger() {
    debugger = this;
  }

  public static void reportSyntaxElement(final Class<? extends Tags> type,
      final SourceSection source) {
    Map<SourceSection, Set<Class<? extends Tags>>> sections = loadedSourcesTags.computeIfAbsent(
        source.getSource(), s -> new HashMap<>());
    Set<Class<? extends Tags>> tags = sections.computeIfAbsent(source, s -> new HashSet<>(2));
    tags.add(type);

    createSourceId(source.getSource());

    createSourceSectionId(source);
  }

  private static String createSourceId(final Source source) {
    return sourcesId.computeIfAbsent(source, src -> {
      int n = nextSourceId;
      nextSourceId += 1;
      String id = "s-" + n;
      idSources.put(id, src);
      return id;
    });
  }

  private static String createSourceSectionId(final SourceSection source) {
    return sourceSectionId.computeIfAbsent(source, s -> {
      int n = nextSourceSectionId;
      nextSourceSectionId += 1;
      return "ss-" + n;
    });
  }

  private static final ArrayList<Source> notReady = new ArrayList<>();

  public static void reportLoadedSource(final Source source) {
    if (debugger == null || debugger.webSocketServer == null || client == null) {
      notReady.add(source);
      return;
    }

    ensureConnectionIsAvailable();

    if (!notReady.isEmpty()) {
      for (Source s : notReady) {
        String json = createSourceAndSectionMessage(s);
        client.send(json);
      }
      notReady.clear();
    }

    String json = createSourceAndSectionMessage(source);
    client.send(json);
  }

  private static void ensureConnectionIsAvailable() {
    assert debugger != null;
    assert debugger.webSocketServer != null;
    assert client != null;

    assert client.isOpen();
  }

  private static String createSourceAndSectionMessage(final Source source) {
    return JsonWriter.createJson("source", loadedSourcesTags.get(source), sourcesId, sourceSectionId);
  }

  public static void reportRootNodeAfterParsing(final RootNode rootNode) {
    assert rootNode.getSourceSection() != null : "RootNode without source section";
    Set<RootNode> roots = rootNodes.computeIfAbsent(
        rootNode.getSourceSection().getSource(), s -> new HashSet<>());
    assert !roots.contains(rootNode) : "This method was parsed twice? should not happen";
    roots.add(rootNode);
  }

  public static void reportExecutionEvent(final ExecutionEvent e) {
    truffleDebugger = e.getDebugger();
    // TODO: prepare step and continue???
  }


  private static int nextSuspendEventId = 0;
  private static final Map<String, SuspendedEvent> suspendEvents  = new HashMap<>();
  private static final Map<String, CompletableFuture<Object>> suspendFutures = new HashMap<>();


  private static String getNextSuspendEventId() {
    int id = nextSuspendEventId;
    nextSuspendEventId += 1;
    return "se-" + id;
  }

  private static JSONObjectBuilder createJsonForSourceSections(final Source source) {
    Set<SourceSection> sections = new HashSet<>();

    Map<SourceSection, Set<Class<? extends Tags>>> tagsForSections = loadedSourcesTags.get(source);
    Tagging.collectSourceSectionsAndTags(rootNodes.get(source), tagsForSections, instrumenter);

    for (SourceSection section : tagsForSections.keySet()) {
      if (section.getSource() == source) {
        sections.add(section);
        createSourceSectionId(section);
      }
    }

    return JsonWriter.createJsonForSourceSections(sourcesId, sourceSectionId, sections, tagsForSections);
  }

  public static void reportSuspendedEvent(final SuspendedEvent e) {
    Node     suspendedNode = e.getNode();
    RootNode suspendedRoot = suspendedNode.getRootNode();
    Source suspendedSource = suspendedRoot.getSourceSection().getSource();

    JSONObjectBuilder builder  = JSONHelper.object();
    builder.add("type", "suspendEvent");

    // first add the source info, because this builds up also tag info
    builder.add("sourceId", sourcesId.get(suspendedSource));
    builder.add("sections", createJsonForSourceSections(suspendedSource));

    JSONArrayBuilder stackJson = JSONHelper.array();
    List<FrameInstance> stack = e.getStack();


    for (int stackIndex = 0; stackIndex < stack.size(); stackIndex++) {
      final Node callNode = stackIndex == 0 ? suspendedNode : stack.get(stackIndex).getCallNode();
      stackJson.add(createFrame(callNode, stack.get(stackIndex)));
    }
    builder.add("stack", stackJson);

    builder.add("topFrame", createTopFrameJson(e.getFrame(), suspendedRoot));

    String id = getNextSuspendEventId();
    builder.add("id", id);

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

  private static void log(final String str) {
    // Checkstyle: stop
    System.out.println(str);
    // Checkstyle: resume
  }

  private static JSONObjectBuilder createTopFrameJson(final MaterializedFrame frame, final RootNode root) {
    JSONArrayBuilder arguments = JSONHelper.array();
    for (Object o : frame.getArguments()) {
      arguments.add(o.toString());
    }

    JSONObjectBuilder slots = JSONHelper.object();
    for (FrameSlot slot : root.getFrameDescriptor().getSlots()) {
      Object value = frame.getValue(slot);
      slots.add(slot.getIdentifier().toString(),
          Objects.toString(value));
    }

    JSONObjectBuilder frameJson = JSONHelper.object();
    frameJson.add("arguments", arguments);
    frameJson.add("slots", slots);
    return frameJson;
  }

  private static JSONObjectBuilder createFrame(final Node node, final FrameInstance stackFrame) {
    JSONObjectBuilder frame = JSONHelper.object();
    if (node != null) {
      SourceSection section = node.getEncapsulatingSourceSection();
      if (section != null) {
        Map<SourceSection, Set<Class<? extends Tags>>> tagsForSections = loadedSourcesTags.get(section.getSource());
        frame.add("sourceSection", JsonWriter.sectionToJson(
            section, createSourceSectionId(section), sourcesId,
            (tagsForSections == null) ? null : tagsForSections.get(section)));
      }
    }

    RootCallTarget rct = (RootCallTarget) stackFrame.getCallTarget();
    SourceSection rootSource = rct.getRootNode().getSourceSection();
    String methodName;
    if (rootSource != null) {
      methodName = rootSource.getIdentifier();
    } else {
      methodName = rct.toString();
    }
    frame.add("methodName", methodName);

    // TODO: stack frame content, or on demand?
    // stackFrame.getFrame(FrameAccess.READ_ONLY, true);
    return frame;
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

    JSONArrayBuilder actors = JSONHelper.array();
    for (Entry<SFarReference, String> e : actorsToIds.entrySet()) {
      actors.add(JsonSerializer.toJson(e.getValue(), e.getKey()));
    }

    int mId = 0;
    JSONObjectBuilder messages = JSONHelper.object();

    Map<Actor, Set<JSONObjectBuilder>> perReceiver = new HashMap<>();
    for (ObjectBuffer<ObjectBuffer<EventualMessage>> perThread : messagesPerThread) {
      for (ObjectBuffer<EventualMessage> perBatch : perThread) {
        for (EventualMessage m : perBatch) {
          perReceiver.computeIfAbsent(m.getTarget(), a -> new HashSet<>());

          JSONObjectBuilder jsonM = JSONHelper.object();
          jsonM.add("id", "m-" + mId);
          mId += 1;
          assert actorObjsToIds.containsKey(m.getSender());
          assert actorObjsToIds.containsKey(m.getTarget());
          jsonM.add("sender", actorObjsToIds.get(m.getSender()));
          jsonM.add("receiver", actorObjsToIds.get(m.getTarget()));
          perReceiver.get(m.getTarget()).add(jsonM);
        }
      }
    }

    for (Entry<Actor, Set<JSONObjectBuilder>> e : perReceiver.entrySet()) {
      JSONArrayBuilder arr = JSONHelper.array();
      for (JSONObjectBuilder m : e.getValue()) {
        arr.add(m);
      }
      messages.add(actorObjsToIds.get(e.getKey()), arr);
    }

    JSONObjectBuilder history = JSONHelper.object();
    history.add("messages", messages); // TODO
    history.add("actors", actors);

    JSONObjectBuilder msg = JSONHelper.object();
    msg.add("type", "messageHistory");
    msg.add("messageHistory", history);

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

  private Map<SFarReference, String> createActorMap(
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

    // Checkstyle: stop
    try {
      System.out.println("[DEBUGGER] Initialize HTTP and WebSocket Server for Debugger");
      int port = 8889;
      initializeWebSocket(8889);
      System.out.println("[DEBUGGER] Started WebSocket Server");

      port = 8888;
      initializeHttpServer(port);
      System.out.println("[DEBUGGER] Started HTTP Server");
      System.out.println("[DEBUGGER]   URL: http://localhost:" + port + "/index.html");
    } catch (IOException e) {
      e.printStackTrace();
      System.out.println("Failed starting WebSocket and/or HTTP Server");
    }

    assert clientConnected != null;
    System.out.println("[DEBUGGER] Waiting for debugger to connect.");
    try {
      client = clientConnected.get();
//      System.out.println("SLEEEP");
//      Thread.sleep(Long.MAX_VALUE);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (ExecutionException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    System.out.println("[DEBUGGER] Debugger connected.");
    // Checkstyle: resume
  }

  private void initializeHttpServer(final int port) throws IOException {
    if (httpServer == null) {
      InetSocketAddress address = new InetSocketAddress(port);
      httpServer = HttpServer.create(address, 0);
      httpServer.createContext("/", new WebHandler());
      httpServer.setExecutor(null);
      httpServer.start();
    }
  }

  private static class WebHandler implements HttpHandler {

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
      log("[REQ] " + exchange.getRequestURI().toString());
      String rootFolder = "/Users/smarr/Projects/SOM/SOMns/tools";
      String requestedFile = exchange.getRequestURI().toString();
      if (requestedFile.equals("/")) {
        requestedFile = "/index.html";
      }

      switch (requestedFile) {
        case "/index.html":
        case "/view.js":
        case "/vm-connection.js":
        case "/controller.js":
        case "/source.js":
        case "/visualizations.js":
          File f = new File(rootFolder + requestedFile);
          exchange.sendResponseHeaders(200, f.length());
          copy(f, exchange.getResponseBody());
          return;
        case "/favicon.ico":
          exchange.sendResponseHeaders(404, 0);
          exchange.close();
          return;
      }

      log("[REQ] not yet implemented");
      throw new NotYetImplementedException();
    }

    private static void copy(final File f, final OutputStream out) throws IOException {
      byte[] buf = new byte[8192];

      InputStream in = new FileInputStream(f);

      int c = 0;
      while ((c = in.read(buf, 0, buf.length)) > 0) {
        out.write(buf, 0, c);
//        out.flush();
      }

      out.close();
      in.close();
    }
  }

  private void initializeWebSocket(final int port) {
    if (webSocketServer == null) {
      clientConnected = new CompletableFuture<WebSocket>();
      InetSocketAddress addess = new InetSocketAddress(port);
      webSocketServer = new WebSocketHandler(
          addess, (CompletableFuture<WebSocket>) clientConnected);
      webSocketServer.start();
    }
  }

  private static class WebSocketHandler extends WebSocketServer {
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
      log("onClose: code=" + code + " " + reason);
    }

    @Override
    public void onMessage(final WebSocket conn, final String message) {
      JsonObject msg = Json.parse(message).asObject();

      switch(msg.getString("action", null)) {
        case "updateBreakpoint":
          log("UPDATE BREAKPOINT");
          String sourceId   = msg.getString("sourceId", null);
          String sourceName = msg.getString("sourceName", null);
          int lineNumber    = msg.getInt("line", -1);
          boolean enabled   = msg.getBoolean("enabled", false);
          log(sourceId + ":" + lineNumber + " " + enabled);

          Source source = idSources.get(sourceId);
          LineLocation line = source.createLineLocation(lineNumber);

          assert truffleDebugger != null : "debugger has not be initialized yet";
          Breakpoint bp = truffleDebugger.getBreakpoint(line);

          if (enabled && bp == null) {
            try {
              log("SetLineBreakpoint line:" + line);
              Breakpoint newBp = truffleDebugger.setLineBreakpoint(0, line, false);
              assert newBp != null;
            } catch (IOException e) {
              e.printStackTrace();
            }
          } else if (bp != null) {
            bp.setEnabled(enabled);
          }
          return;
        case "stepInto": {
          String id = msg.getString("suspendEvent", null);
          SuspendedEvent event = suspendEvents.get(id);
          assert event != null : "didn't find SuspendEvent";
          event.prepareStepInto(1);
          suspendFutures.get(id).complete(new Object());
          return;
        }
        case "stepOver": {
          String id = msg.getString("suspendEvent", null);
          SuspendedEvent event = suspendEvents.get(id);
          assert event != null : "didn't find SuspendEvent";
          event.prepareStepOver(1);
          suspendFutures.get(id).complete(new Object());
          return;
        }
        case "return": {
          String id = msg.getString("suspendEvent", null);
          SuspendedEvent event = suspendEvents.get(id);
          assert event != null : "didn't find SuspendEvent";
          event.prepareStepOut();
          suspendFutures.get(id).complete(new Object());
          return;
        }
        case "resume": {
          String id = msg.getString("suspendEvent", null);
          SuspendedEvent event = suspendEvents.get(id);
          assert event != null : "didn't find SuspendEvent";
          event.prepareContinue();
          suspendFutures.get(id).complete(new Object());
          return;
        }
      }

      log("not supported: onMessage: " + message);
    }

    @Override
    public void onError(final WebSocket conn, final Exception ex) {
      log("error:");
      ex.printStackTrace();
    }
  }
}
