package tools.debugger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
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

import som.VM;
import som.interpreter.actors.Actor;
import som.vm.Activity;
import som.vm.ActivityThread;
import tools.SourceCoordinate;
import tools.TraceData;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.InitialBreakpointsMessage;
import tools.debugger.message.Message.IncommingMessage;
import tools.debugger.message.Message.OutgoingMessage;
import tools.debugger.message.ProgramInfoRequest;
import tools.debugger.message.ProgramInfoResponse;
import tools.debugger.message.ScopesRequest;
import tools.debugger.message.ScopesResponse;
import tools.debugger.message.SourceMessage;
import tools.debugger.message.StackTraceRequest;
import tools.debugger.message.StackTraceResponse;
import tools.debugger.message.StepMessage.Resume;
import tools.debugger.message.StepMessage.Return;
import tools.debugger.message.StepMessage.StepInto;
import tools.debugger.message.StepMessage.StepOver;
import tools.debugger.message.StepMessage.Stop;
import tools.debugger.message.StoppedMessage;
import tools.debugger.message.SymbolMessage;
import tools.debugger.message.TraceDataRequest;
import tools.debugger.message.UpdateBreakpoint;
import tools.debugger.message.VariablesRequest;
import tools.debugger.message.VariablesResponse;
import tools.debugger.session.AsyncMessageAfterExecutionBreakpoint;
import tools.debugger.session.AsyncMessageBeforeExecutionBreakpoint;
import tools.debugger.session.BreakpointInfo;
import tools.debugger.session.Breakpoints;
import tools.debugger.session.ChannelOppositeBreakpoint;
import tools.debugger.session.LineBreakpoint;
import tools.debugger.session.MessageReceiverBreakpoint;
import tools.debugger.session.MessageSenderBreakpoint;
import tools.debugger.session.PromiseResolutionBreakpoint;
import tools.debugger.session.PromiseResolverBreakpoint;


/**
 * The WebDebugger connects the Truffle debugging facilities with a HTML5
 * application using WebSockets and JSON.
 */
@Registration(id = WebDebugger.ID, name = "WebDebugger", version = "0.1", services = {WebDebugger.class})
public class WebDebugger extends TruffleInstrument implements SuspendedCallback {

  public static final String ID = "web-debugger";

  private FrontendConnector connector;
  private Instrumenter      instrumenter;
  private Breakpoints       breakpoints;

  @CompilationFinal VM vm;

  private final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> loadedSourcesTags = new HashMap<>();
  private final Map<Source, Set<RootNode>> rootNodes = new HashMap<>();

  private final Map<Activity, Suspension> activityToSuspension = new HashMap<>();
  private final Map<Long, Suspension> idToSuspension           = new HashMap<>();

  /** Actors that have been suspended at least once. */
  private final Set<Actor> suspendedActors = Collections.newSetFromMap(new WeakHashMap<>());

  public void reportSyntaxElement(final Class<? extends Tags> type,
      final SourceSection source) {
    Map<SourceSection, Set<Class<? extends Tags>>> sections = loadedSourcesTags.computeIfAbsent(
        source.getSource(), s -> new HashMap<>());
    Set<Class<? extends Tags>> tags = sections.computeIfAbsent(source, s -> new HashSet<>(2));
    tags.add(type);
  }

  public void reportLoadedSource(final Source source) {
    // register source URI as symbol to make sure it's send to the debugger
    SourceCoordinate.getURI(source);
    connector.sendLoadedSource(source, loadedSourcesTags, rootNodes);
  }

  public void reportRootNodeAfterParsing(final RootNode rootNode) {
    assert rootNode.getSourceSection() != null : "RootNode without source section";
    Set<RootNode> roots = rootNodes.computeIfAbsent(
        rootNode.getSourceSection().getSource(), s -> new HashSet<>());
    assert !roots.contains(rootNode) : "This method was parsed twice? should not happen";
    roots.add(rootNode);
  }

  public void prepareSteppingUntilNextRootNode() {
    breakpoints.prepareSteppingUntilNextRootNode();
  }

  public void prepareSteppingAfterNextRootNode() {
    breakpoints.prepareSteppingAfterNextRootNode();
  }

  Suspension getSuspension(final long activityId) {
    return idToSuspension.get(activityId);
  }

  private synchronized Suspension getSuspension(final Activity activity) {
    Suspension suspension = activityToSuspension.get(activity);
    if (suspension == null) {
      long id = activity.getId();
      assert TraceData.isWithinJSIntValueRange(id);
      suspension = new Suspension(activity, id);

      activityToSuspension.put(activity, suspension);
      idToSuspension.put(id, suspension);
    }
    return suspension;
  }


  private Suspension getSuspension() {
    Thread thread = Thread.currentThread();
    Activity current;
    if (thread instanceof ActivityThread) {
      current = ((ActivityThread) thread).getActivity();
      if (current instanceof Actor) {
        synchronized (suspendedActors) {
          suspendedActors.add((Actor) current);
        }
      }
    } else {
      throw new RuntimeException("Support for " + thread.getClass().getName() + " not yet implemented.");
    }
    return getSuspension(current);
  }

  @Override
  public void onSuspend(final SuspendedEvent e) {
    Suspension suspension = getSuspension();
    suspension.update(e);

    connector.sendStoppedMessage(suspension);
    suspension.suspend();
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
    connector.shutdown();
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
    connector = new FrontendConnector(breakpoints, instrumenter, this,
        createJsonProcessor());
    connector.awaitClient();
  }

  public Breakpoints getBreakpoints() {
    return breakpoints;
  }

  // TODO: to be removed
  private static final String INITIAL_BREAKPOINTS = "initialBreakpoints";
  private static final String UPDATE_BREAKPOINT   = "updateBreakpoint";

  public static Gson createJsonProcessor() {
    ClassHierarchyAdapterFactory<OutgoingMessage> outMsgAF = new ClassHierarchyAdapterFactory<>(OutgoingMessage.class, "type");
    outMsgAF.register("source",       SourceMessage.class);
    outMsgAF.register("StoppedEvent", StoppedMessage.class);
    outMsgAF.register("SymbolMessage",       SymbolMessage.class);
    outMsgAF.register("StackTraceResponse",  StackTraceResponse.class);
    outMsgAF.register("ScopesResponse",      ScopesResponse.class);
    outMsgAF.register("VariablesResponse",   VariablesResponse.class);
    outMsgAF.register("ProgramInfoResponse", ProgramInfoResponse.class);

    ClassHierarchyAdapterFactory<IncommingMessage> inMsgAF = new ClassHierarchyAdapterFactory<>(IncommingMessage.class, "action");
    inMsgAF.register(INITIAL_BREAKPOINTS, InitialBreakpointsMessage.class);
    inMsgAF.register(UPDATE_BREAKPOINT,   UpdateBreakpoint.class);
    inMsgAF.register("stepInto", StepInto.class);
    inMsgAF.register("stepOver", StepOver.class);
    inMsgAF.register("return",   Return.class);
    inMsgAF.register("resume",   Resume.class);
    inMsgAF.register("stop",     Stop.class);
    inMsgAF.register("StackTraceRequest", StackTraceRequest.class);
    inMsgAF.register("ScopesRequest",     ScopesRequest.class);
    inMsgAF.register("VariablesRequest",  VariablesRequest.class);
    inMsgAF.register("ProgramInfoRequest", ProgramInfoRequest.class);
    inMsgAF.register("TraceDataRequest",  TraceDataRequest.class);

    ClassHierarchyAdapterFactory<BreakpointInfo> breakpointAF = new ClassHierarchyAdapterFactory<>(BreakpointInfo.class, "type");
    breakpointAF.register(LineBreakpoint.class);
    breakpointAF.register(MessageSenderBreakpoint.class);
    breakpointAF.register(MessageReceiverBreakpoint.class);
    breakpointAF.register(AsyncMessageBeforeExecutionBreakpoint.class);
    breakpointAF.register(AsyncMessageAfterExecutionBreakpoint.class);
    breakpointAF.register(PromiseResolutionBreakpoint.class);
    breakpointAF.register(PromiseResolverBreakpoint.class);
    breakpointAF.register(ChannelOppositeBreakpoint.class);

    return new GsonBuilder().
        registerTypeAdapterFactory(outMsgAF).
        registerTypeAdapterFactory(inMsgAF).
        registerTypeAdapterFactory(breakpointAF).
        create();
  }
}
