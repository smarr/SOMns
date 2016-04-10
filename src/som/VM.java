package som;

import java.io.IOException;
import java.util.Map;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.debug.ExecutionEvent;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.EventConsumer;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.api.vm.PolyglotEngine.Instrument;
import com.oracle.truffle.tools.TruffleProfiler;
import com.oracle.truffle.tools.debug.shell.client.SimpleREPLClient;
import com.oracle.truffle.tools.debug.shell.server.REPLServer;

import som.compiler.MixinDefinition;
import som.interpreter.SomLanguage;
import som.interpreter.TruffleCompiler;
import som.interpreter.actors.Actor;
import som.interpreter.actors.SFarReference;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.vm.ObjectSystem;
import som.vmobjects.SInvokable;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import tools.debugger.WebDebugger;
import tools.dym.DynamicMetrics;
import tools.dym.profiles.StructuralProbe;
import tools.highlight.Highlight;
import tools.highlight.Tags;


public final class VM {

  @CompilationFinal private static PolyglotEngine engine;
  @CompilationFinal private static VM vm;
  @CompilationFinal private static VMOptions vmOptions;
  @CompilationFinal private static StructuralProbe structuralProbes;

  public static PolyglotEngine getEngine() {
    return engine;
  }

  private final boolean avoidExitForTesting;
  private final ObjectSystem objectSystem;

  private int lastExitCode = 0;
  private volatile boolean shouldExit = false;
  private final VMOptions options;
  private boolean usesActors;
  private Thread mainThread;

  @CompilationFinal
  private SObjectWithoutFields vmMirror;
  @CompilationFinal
  private Actor mainActor;

  public static final boolean DebugMode = false;
  public static final boolean FailOnMissingOptimizations = false;

  public static void thisMethodNeedsToBeOptimized(final String msg) {
    if (FailOnMissingOptimizations) {
      CompilerAsserts.neverPartOfCompilation(msg);
    }
  }

  public static void callerNeedsToBeOptimized(final String msg) {
    if (FailOnMissingOptimizations) {
      CompilerAsserts.neverPartOfCompilation(msg);
    }
  }

  public static boolean enabledDynamicMetricsTool() {
    return vmOptions.dynamicMetricsEnabled;
  }

  public static void insertInstrumentationWrapper(final Node node) {
    if (vmOptions.anyInstrumentationEnabled) {
      assert node.getSourceSection() != null || (node instanceof WrapperNode) : "Node needs source section, or needs to be wrapper";
      // TODO: a way to check whether the node needs actually wrapping?
//      String[] tags = node.getSourceSection().getTags();
//      if (tags != null && tags.length > 0) {
        InstrumentationHandler.insertInstrumentationWrapper(node);
//      }
    }
  }

  public static StructuralProbe getStructuralProbe() {
    return structuralProbes;
  }

  public static void reportNewMixin(final MixinDefinition m) {
    structuralProbes.recordNewClass(m);
  }

  public static void reportNewMethod(final SInvokable m) {
    structuralProbes.recordNewMethod(m);
  }

  public VM(final String[] args, final boolean avoidExitForTesting) throws IOException {
    vm = this;

    // TODO: fix hack, we need this early, and we want tool/polyglot engine support for the events...
    structuralProbes = new StructuralProbe();

    this.avoidExitForTesting = avoidExitForTesting;
    options = new VMOptions(args);
    vmOptions = options;
    objectSystem = new ObjectSystem(options.platformFile, options.kernelFile);

    if (options.showUsage) {
      VMOptions.printUsageAndExit();
    }
  }

  public VM(final String[] args) throws IOException {
    this(args, false);
  }

  public static void reportSyntaxElement(final Class<? extends Tags> type,
      final SourceSection source) {
    Highlight.reportNonAstSyntax(type, source);
    WebDebugger.reportSyntaxElement(type, source);
  }

  public static void reportParsedRootNode(final RootNode rootNode) {
    Highlight.reportParsedRootNode(rootNode);
    WebDebugger.reportRootNodeAfterParsing(rootNode);
  }

  public static void reportLoadedSource(final Source source) {
    WebDebugger.reportLoadedSource(source);
  }

  public static void reportSuspendedEvent(final SuspendedEvent e) {
    WebDebugger.reportSuspendedEvent(e);
  }

  public static boolean shouldExit() {
    return vm.shouldExit;
  }

  public int lastExitCode() {
    return lastExitCode;
  }

  public static boolean isUsingActors() {
    return vm.usesActors;
  }

  public static void hasSendMessages() {
    vm.usesActors = true;
  }

  public static String[] getArguments() {
    return vm.options.args;
  }

  public static void exit(final int errorCode) {
    vm.exitVM(errorCode);
  }

  private void exitVM(final int errorCode) {
    TruffleCompiler.transferToInterpreter("exit");
    // Exit from the Java system
    if (!avoidExitForTesting) {
      engine.dispose();
      System.exit(errorCode);
    } else {
      lastExitCode = errorCode;
      shouldExit = true;
    }
  }

  public static void errorExit(final String message) {
    TruffleCompiler.transferToInterpreter("errorExit");
    errorPrintln("Runtime Error: " + message);
    exit(1);
  }

  @TruffleBoundary
  public static void errorPrint(final String msg) {
    // Checkstyle: stop
    System.err.print(msg);
    // Checkstyle: resume
  }

  @TruffleBoundary
  public static void errorPrintln(final String msg) {
    // Checkstyle: stop
    System.err.println(msg);
    // Checkstyle: resume
  }

  @TruffleBoundary
  public static void errorPrintln() {
    // Checkstyle: stop
    System.err.println();
    // Checkstyle: resume
  }

  @TruffleBoundary
  public static void print(final String msg) {
    // Checkstyle: stop
    System.out.print(msg);
    // Checkstyle: resume
  }

  @TruffleBoundary
  public static void println(final String msg) {
    // Checkstyle: stop
    System.out.println(msg);
    // Checkstyle: resume
  }

  public static boolean isAvoidingExit() {
    return vm.avoidExitForTesting;
  }

  public static void setMainThread(final Thread t) {
    vm.mainThread = t;
  }

  public static Thread getMainThread() {
    return vm.mainThread;
  }

  public void initalize() {
    assert vmMirror  == null : "VM seems to be initialized already";
    assert mainActor == null : "VM seems to be initialized already";

    mainActor = Actor.initializeActorSystem();
    vmMirror  = objectSystem.initialize();
  }

  public Object execute(final String selector) {
    return objectSystem.execute(selector);
  }

  public void execute() {
    objectSystem.executeApplication(vmMirror, mainActor);
  }

  public static void main(final String[] args) {
    Builder builder = PolyglotEngine.newBuilder();
    builder.config(SomLanguage.MIME_TYPE, SomLanguage.CMD_ARGS, args);
    vmOptions = new VMOptions(args);

    if (vmOptions.debuggerEnabled) {
      startDebugger(builder);
    } else {
      startExecution(builder);
    }
  }

  private static void startDebugger(final Builder builder) {
    SimpleREPLClient client = new SimpleREPLClient();
    REPLServer server = new REPLServer(client, builder);
    server.start();
    client.start(server);
  }

  private static final EventConsumer<ExecutionEvent> onExec =
      new EventConsumer<ExecutionEvent>(ExecutionEvent.class) {
    @Override
    protected void on(final ExecutionEvent event) {
      WebDebugger.reportExecutionEvent(event);
    }
  };

  private static final EventConsumer<SuspendedEvent> onHalted =
      new EventConsumer<SuspendedEvent>(SuspendedEvent.class) {
    @Override
    protected void on(final SuspendedEvent e) {
      WebDebugger.reportSuspendedEvent(e);
    }
  };

  private static void startExecution(final Builder builder) {
    if (vmOptions.webDebuggerEnabled) {
      builder.onEvent(onExec).onEvent(onHalted);
    }
    engine = builder.build();

    try {
      Map<String, Instrument> instruments = engine.getInstruments();
      Instrument profiler = instruments.get(TruffleProfiler.ID);
      if (vmOptions.profilingEnabled && profiler == null) {
        VM.errorPrintln("Truffle profiler not available. Might be a class path issue");
      } else if (profiler != null) {
        profiler.setEnabled(vmOptions.profilingEnabled);
      }
      instruments.get(Highlight.ID).setEnabled(vmOptions.highlightingEnabled);

      if (vmOptions.webDebuggerEnabled) {
        instruments.get(WebDebugger.ID).setEnabled(true);
      }

      if (vmOptions.dynamicMetricsEnabled) {
        instruments.get(DynamicMetrics.ID).setEnabled(true);
      }

      engine.eval(SomLanguage.START);
      engine.dispose();
    } catch (IOException e) {
      throw new RuntimeException("This should never happen", e);
    }
    System.exit(vm.lastExitCode);
  }

  public static MixinDefinition loadModule(final String filename) throws IOException {
    return vm.objectSystem.loadModule(filename);
  }

  /** This is only meant to be used in unit tests. */
  public static void resetClassReferences(final boolean callFromUnitTest) {
    assert callFromUnitTest;
    SFarReference.setSOMClass(null);
    SPromise.setPairClass(null);
    SPromise.setSOMClass(null);
    SResolver.setSOMClass(null);
  }
}
