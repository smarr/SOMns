package som;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.api.vm.PolyglotEngine.Instrument;
import com.oracle.truffle.tools.ProfilerInstrument;
import com.oracle.truffle.tools.debug.shell.client.SimpleREPLClient;
import com.oracle.truffle.tools.debug.shell.server.REPLServer;

import coveralls.truffle.Coverage;
import som.compiler.MixinDefinition;
import som.compiler.SourcecodeCompiler;
import som.interpreter.SomLanguage;
import som.interpreter.TruffleCompiler;
import som.interpreter.actors.Actor;
import som.interpreter.actors.SFarReference;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.vm.ObjectSystem;
import som.vm.Primitives;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import tools.actors.ActorExecutionTrace;
import tools.debugger.WebDebugger;
import tools.dym.DynamicMetrics;
import tools.highlight.Highlight;
import tools.highlight.Tags;
import tools.language.StructuralProbe;


public final class VM {

  @CompilationFinal private static PolyglotEngine engine;
  @CompilationFinal private static VM vm;
  @CompilationFinal private static StructuralProbe structuralProbe;
  @CompilationFinal private static WebDebugger webDebugger;

  public static WebDebugger getWebDebugger() {
    return webDebugger;
  }

  private final Map<String, Object> exports = new HashMap<>();

  public boolean registerExport(final String name, final Object value) {
    boolean wasExportedAlready = exports.containsKey(name);
    exports.put(name, value);
    return wasExportedAlready;
  }

  public Object getExport(final String name) {
    return exports.get(name);
  }

  /**
   * @return last VM instance, for tests only
   */
  public static VM getVM() {
    return vm;
  }

  public static void setEngine(final PolyglotEngine e) {
    engine = e;
  }

  private final boolean avoidExitForTesting;
  private final ObjectSystem objectSystem;

  private int lastExitCode = 0;
  private volatile boolean shouldExit = false;
  private volatile CompletableFuture<Object> vmMainCompletion = null;
  private final VMOptions options;

  @CompilationFinal
  private SObjectWithoutFields vmMirror;
  @CompilationFinal
  private Actor mainActor;

  public SObjectWithoutFields getVmMirror() {
    return vmMirror;
  }

  public Primitives getPrimitives() {
    return objectSystem.getPrimitives();
  }

  public static void thisMethodNeedsToBeOptimized(final String msg) {
    if (VmSettings.FAIL_ON_MISSING_OPTIMIZATIONS) {
      CompilerAsserts.neverPartOfCompilation(msg);
    }
  }

  public static void callerNeedsToBeOptimized(final String msg) {
    if (VmSettings.FAIL_ON_MISSING_OPTIMIZATIONS) {
      CompilerAsserts.neverPartOfCompilation(msg);
    }
  }

  public static void insertInstrumentationWrapper(final Node node) {
    // TODO: make thread-safe!!!
    // TODO: can I assert that it is locked?? helper on Node??
    if (VmSettings.INSTRUMENTATION) {
      assert node.getSourceSection() != null || (node instanceof WrapperNode) : "Node needs source section, or needs to be wrapper";
      // TODO: a way to check whether the node needs actually wrapping?
//      String[] tags = node.getSourceSection().getTags();
//      if (tags != null && tags.length > 0) {
        InstrumentationHandler.insertInstrumentationWrapper(node);
//      }
    }
  }

  public VM(final String[] args, final boolean avoidExitForTesting) throws IOException {
    vm = this;

    this.avoidExitForTesting = avoidExitForTesting;
    options = new VMOptions(args);
    objectSystem = new ObjectSystem(new SourcecodeCompiler(), structuralProbe,
        options.platformFile, options.kernelFile);

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
    if (webDebugger != null) {
      webDebugger.reportSyntaxElement(type, source);
    }
  }

  public static void reportParsedRootNode(final RootNode rootNode) {
    Highlight.reportParsedRootNode(rootNode);
    if (webDebugger != null) {
      webDebugger.reportRootNodeAfterParsing(rootNode);
    }
  }

  public static void reportLoadedSource(final Source source) {
    if (webDebugger != null) {
      webDebugger.reportLoadedSource(source);
    }
  }

  public void setCompletionFuture(final CompletableFuture<Object> future) {
    vmMainCompletion = future;
  }

  public static void setVMMainCompletion(final CompletableFuture<Object> future) {
    vm.vmMainCompletion = future;
  }

  public static boolean shouldExit() {
    return vm.shouldExit;
  }

  public int lastExitCode() {
    return lastExitCode;
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

    vmMainCompletion.complete(errorCode);
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

  public void initalize() {
    assert vmMirror  == null : "VM seems to be initialized already";
    assert mainActor == null : "VM seems to be initialized already";

    mainActor = Actor.createActor();
    vmMirror  = objectSystem.initialize();

    ActorExecutionTrace.recordMainActor(mainActor, objectSystem);
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
    VMOptions vmOptions = new VMOptions(args);

    if (vmOptions.debuggerEnabled) {
      startDebugger(builder);
    } else {
      startExecution(builder, vmOptions);
    }
  }

  private static void startDebugger(final Builder builder) {
    SimpleREPLClient client = new SimpleREPLClient();
    REPLServer server = new REPLServer(client, builder);
    engine = server.getEngine();
    Debugger.find(engine);
    server.start();
    client.start(server);
  }


  private static void startExecution(final Builder builder,
      final VMOptions vmOptions) {
    engine = builder.build();

    Map<String, Instrument> instruments = engine.getInstruments();
    Instrument profiler = instruments.get(ProfilerInstrument.ID);
    if (vmOptions.profilingEnabled && profiler == null) {
      VM.errorPrintln("Truffle profiler not available. Might be a class path issue");
    } else if (profiler != null) {
      profiler.setEnabled(vmOptions.profilingEnabled);
    }
    instruments.get(Highlight.ID).setEnabled(vmOptions.highlightingEnabled);

    Debugger debugger = null;
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      debugger = Debugger.find(engine);
    }

    if (vmOptions.webDebuggerEnabled) {
      assert VmSettings.TRUFFLE_DEBUGGER_ENABLED && debugger != null;
      Instrument webDebuggerInst = instruments.get(WebDebugger.ID);
      webDebuggerInst.setEnabled(true);

      webDebugger = webDebuggerInst.lookup(WebDebugger.class);
      webDebugger.startServer(debugger);
    }

    if (vmOptions.coverageEnabled) {
      Instrument coveralls = instruments.get(Coverage.ID);
      coveralls.setEnabled(true);
      Coverage cov = coveralls.lookup(Coverage.class);
      cov.setRepoToken(vmOptions.coverallsRepoToken);
      cov.setServiceName("travis-ci");
      cov.includeTravisData(true);
    }

    if (vmOptions.dynamicMetricsEnabled) {
      assert VmSettings.DYNAMIC_METRICS;
      Instrument dynM = instruments.get(DynamicMetrics.ID);
      dynM.setEnabled(true);
      structuralProbe = dynM.lookup(StructuralProbe.class);
      assert structuralProbe != null : "Initialization of DynamicMetrics tool incomplete";
    }

    engine.eval(SomLanguage.START);
    engine.dispose();
    System.exit(vm.lastExitCode);
  }

  public MixinDefinition loadModule(final String filename) throws IOException {
    return objectSystem.loadModule(filename);
  }

  public MixinDefinition loadModule(final Source source) throws IOException {
    return objectSystem.loadModule(source);
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
