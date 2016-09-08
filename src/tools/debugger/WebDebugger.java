package tools.debugger;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession.SteppingLocation;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import tools.debugger.session.Breakpoints;
import tools.highlight.Tags;


/**
 * The WebDebugger connects the Truffle debugging facilities with a HTML5
 * application using WebSockets and JSON.
 */
@Registration(id = WebDebugger.ID)
public class WebDebugger extends TruffleInstrument implements SuspendedCallback {

  public static final String ID = "web-debugger";

  private FrontendConnector connector;

  private Instrumenter instrumenter;

  private final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> loadedSourcesTags = new HashMap<>();
  private final Map<Source, Set<RootNode>> rootNodes = new HashMap<>();

  private Breakpoints breakpoints;

  private int nextSuspendEventId = 0;
  private final Map<String, SuspendedEvent> suspendEvents  = new HashMap<>();
  private final Map<String, CompletableFuture<Object>> suspendFutures = new HashMap<>();

  public void reportSyntaxElement(final Class<? extends Tags> type,
      final SourceSection source) {
    Map<SourceSection, Set<Class<? extends Tags>>> sections = loadedSourcesTags.computeIfAbsent(
        source.getSource(), s -> new HashMap<>());
    Set<Class<? extends Tags>> tags = sections.computeIfAbsent(source, s -> new HashSet<>(2));
    tags.add(type);

    JsonSerializer.createSourceId(source.getSource());
    JsonSerializer.createSourceSectionId(source);
  }

  public void reportLoadedSource(final Source source) {
    connector.sendLoadedSource(source, loadedSourcesTags, rootNodes);
  }

  public void reportRootNodeAfterParsing(final RootNode rootNode) {
    assert rootNode.getSourceSection() != null : "RootNode without source section";
    Set<RootNode> roots = rootNodes.computeIfAbsent(
        rootNode.getSourceSection().getSource(), s -> new HashSet<>());
    assert !roots.contains(rootNode) : "This method was parsed twice? should not happen";
    roots.add(rootNode);
  }

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

  @Override
  public void onSuspend(final SuspendedEvent e) {
    String id = getNextSuspendEventId();
    CompletableFuture<Object> future = new CompletableFuture<>();
    suspendEvents.put(id, e);
    suspendFutures.put(id, future);

    connector.sendSuspendedEvent(e, id, loadedSourcesTags, rootNodes);

    try {
      future.get();
    } catch (InterruptedException | ExecutionException e1) {
      log("[DEBUGGER] Future failed:");
      e1.printStackTrace();
    }
  }

  public void suspendExecution(final MaterializedFrame haltedFrame,
      final SteppingLocation steppingLocation) {
    breakpoints.doSuspend(haltedFrame, steppingLocation);
  }

  public static void log(final String str) {
    // Checkstyle: stop
    System.out.println(str);
    // Checkstyle: resume
  }

  @Override
  protected void onDispose(final Env env) {
    connector.sendActorHistory();
    connector.shutdown();
  }

  @Override
  protected void onCreate(final Env env) {
    instrumenter = env.getInstrumenter();
    env.registerService(this);
  }

  public void startServer(final Debugger dbg) {
    breakpoints = new Breakpoints(dbg, this);
    connector = new FrontendConnector(breakpoints, instrumenter, this);
    connector.awaitClient();
  }

  public Source getSource(final URI sourceUri) {
    for (Source source : this.rootNodes.keySet()) {
      if ((source.getURI()).equals(sourceUri)) {
        return source;
      }
    }
    return null;
  }

  public Set<RootNode> getRootNodesBySource(final Source source) {
    return rootNodes.get(source);
  }

  public Set<RootNode> getRootNodesBySource(final URI sourceUri) {
    Set<RootNode> roots = null;
    Source source = getSource(sourceUri);
    if (source != null) {
      roots = rootNodes.get(source);
    }
    return roots;
  }

  public Breakpoints getBreakpoints() {
    return breakpoints;
  }
}
