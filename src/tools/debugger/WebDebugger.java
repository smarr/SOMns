package tools.debugger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;

import com.google.gson.Gson;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import bd.source.SourceCoordinate;
import som.VM;
import som.interpreter.actors.Actor;
import som.vm.Activity;
import som.vm.Symbols;
import tools.TraceData;
import tools.concurrency.TracingActivityThread;
import tools.concurrency.TracingActors;
import tools.debugger.frontend.Suspension;
import tools.debugger.breakpoints.Breakpoints;


/**
 * The WebDebugger connects the Truffle debugging facilities with a HTML5
 * application using WebSockets and JSON.
 */
@Registration(id = WebDebugger.ID, name = "WebDebugger", version = "0.1",
    services = {WebDebugger.class})
public class WebDebugger extends TruffleInstrument implements SuspendedCallback {

  static final String ID = "web-debugger";

  public static WebDebugger find(final TruffleLanguage.Env env) {
    InstrumentInfo instrument = env.getInstruments().get(ID);
    if (instrument == null) {
      throw new IllegalStateException(
          "WebDebugger not properly installed into polyglot.Engine");
    }

    return env.lookup(instrument, WebDebugger.class);
  }

  public static WebDebugger find(final Engine engine) {
    Instrument instrument = engine.getInstruments().get(ID);
    if (instrument == null) {
      throw new IllegalStateException(
          "WebDebugger not properly installed into polyglot.Engine");
    }

    return instrument.lookup(WebDebugger.class);
  }

  private FrontendConnector connector;
  private Instrumenter      instrumenter;
  private Breakpoints       breakpoints;

  @CompilationFinal VM vm;

  private final Map<Source, Map<SourceSection, Set<Class<? extends Tag>>>> loadedSourcesTags =
      new HashMap<>();

  private final Map<Source, Set<RootNode>> rootNodes = new HashMap<>();

  private final Map<Activity, Suspension> activityToSuspension = new HashMap<>();
  private final Map<Long, Suspension>     idToSuspension       = new HashMap<>();

  public void reportSyntaxElement(final Class<? extends Tag> type,
      final SourceSection source) {
    Map<SourceSection, Set<Class<? extends Tag>>> sections =
        loadedSourcesTags.computeIfAbsent(
            source.getSource(), s -> new HashMap<>());
    Set<Class<? extends Tag>> tags = sections.computeIfAbsent(source, s -> new HashSet<>(2));
    tags.add(type);
  }

  public void reportLoadedSource(final Source source) {
    // register source URI as symbol to make sure it's send to the debugger
    Symbols.symbolFor(SourceCoordinate.getURI(source));
    connector.sendLoadedSource(source, loadedSourcesTags, rootNodes);
  }

  public void reportRootNodeAfterParsing(final RootNode rootNode) {
    assert rootNode.getSourceSection() != null : "RootNode without source section";
    Set<RootNode> roots = rootNodes.computeIfAbsent(
        rootNode.getSourceSection().getSource(), s -> new HashSet<>());
    assert !roots.contains(rootNode) : "This method was parsed twice? should not happen";
    roots.add(rootNode);
  }

  public void prepareSteppingUntilNextRootNode(final Thread thread) {
    breakpoints.prepareSteppingUntilNextRootNode(thread);
  }

  public void prepareSteppingAfterNextRootNode(final Thread thread) {
    breakpoints.prepareSteppingAfterNextRootNode(thread);
  }

  public Suspension getSuspension(final long activityId) {
    return idToSuspension.get(activityId);
  }

  private synchronized Suspension getSuspension(final Activity activity,
      final TracingActivityThread activityThread) {
    Suspension suspension = activityToSuspension.get(activity);
    if (suspension == null) {
      long id = activity.getId();
      assert TraceData.isWithinJSIntValueRange(id);
      suspension = new Suspension(activityThread, activity, id);

      activityToSuspension.put(activity, suspension);
      idToSuspension.put(id, suspension);
    }
    return suspension;
  }

  private Suspension getSuspension() {
    Thread thread = Thread.currentThread();
    Activity current;
    TracingActivityThread activityThread;
    if (thread instanceof TracingActivityThread) {
      activityThread = (TracingActivityThread) thread;
      current = activityThread.getActivity();
    } else {
      throw new RuntimeException(
          "Support for " + thread.getClass().getName() + " not yet implemented.");
    }
    return getSuspension(current, activityThread);
  }

  @Override
  public void onSuspend(final SuspendedEvent e) {
    Suspension suspension = getSuspension();
    suspension.update(e);

    connector.sendStoppedMessage(suspension);
    suspension.suspend();
  }

  public static void log(final String str) {
    // Checkstyle: stop
    System.out.println(str);
    // Checkstyle: resume
  }

  @Override
  protected void onDispose(final Env env) {
    /* NOOP: we close sockets with a VM shutdown hook */
  }

  @Override
  protected void onCreate(final Env env) {
    instrumenter = env.getInstrumenter();
    env.registerService(this);
  }

  public void startServer(final Debugger dbg, final VM vm) {
    assert vm != null;
    this.vm = vm;
    breakpoints = new Breakpoints(dbg, this);
    connector = new FrontendConnector(breakpoints, instrumenter, this, jsonProcessor);
    connector.awaitClient();
  }

  public Breakpoints getBreakpoints() {
    return breakpoints;
  }

  public Actor getActorById(long actorId) {
    return TracingActors.TracingActor.getActorById(actorId);
  }

  private static Gson jsonProcessor = RuntimeReflectionRegistration.createJsonProcessor();
}
