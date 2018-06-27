package som;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler;
import com.oracle.truffle.api.nodes.GraphPrintVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.api.vm.PolyglotEngine.Value;
import com.oracle.truffle.api.vm.PolyglotRuntime.Instrument;
import com.oracle.truffle.tools.Profiler;
import com.oracle.truffle.tools.ProfilerInstrument;

import bd.inlining.InlinableNodes;
import coveralls.truffle.Coverage;
import som.compiler.MixinDefinition;
import som.compiler.SourcecodeCompiler;
import som.interpreter.Method;
import som.interpreter.SomLanguage;
import som.interpreter.TruffleCompiler;
import som.interpreter.actors.Actor;
import som.interpreter.actors.Actor.ActorProcessingThreadFactory;
import som.interpreter.actors.SFarReference;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.primitives.processes.ChannelPrimitives;
import som.primitives.processes.ChannelPrimitives.ProcessThreadFactory;
import som.primitives.threading.TaskThreads.ForkJoinThreadFactory;
import som.primitives.threading.ThreadingModule;
import som.vm.ObjectSystem;
import som.vm.Primitives;
import som.vm.VmOptions;
import som.vm.VmSettings;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import som.vmobjects.SSymbol;
import tools.concurrency.MedeorTrace;
import tools.concurrency.TracingActors;
import tools.concurrency.TracingBackend;
import tools.debugger.Tags;
import tools.debugger.WebDebugger;
import tools.debugger.session.Breakpoints;
import tools.dym.DynamicMetrics;
import tools.language.StructuralProbe;
import tools.superinstructions.CandidateIdentifier;


@SuppressWarnings("deprecation")
public final class VM {

  @CompilationFinal private PolyglotEngine engine;

  @CompilationFinal private StructuralProbe structuralProbe;
  @CompilationFinal private WebDebugger     webDebugger;
  @CompilationFinal private Profiler        truffleProfiler;

  private final ForkJoinPool actorPool;
  private final ForkJoinPool forkJoinPool;
  private final ForkJoinPool processesPool;
  private final ForkJoinPool threadPool;

  private final boolean                  avoidExitForTesting;
  @CompilationFinal private ObjectSystem objectSystem;

  @CompilationFinal private SomLanguage language;

  private int              lastExitCode = 0;
  private volatile boolean shouldExit   = false;
  private final VmOptions  options;

  @CompilationFinal private SObjectWithoutFields vmMirror;
  @CompilationFinal private Actor                mainActor;

  private static final int MAX_THREADS = 0x7fff;

  public VM(final VmOptions vmOptions, final boolean avoidExitForTesting) {
    this.avoidExitForTesting = avoidExitForTesting;
    options = vmOptions;

    actorPool = new ForkJoinPool(VmSettings.NUM_THREADS,
        new ActorProcessingThreadFactory(), new UncaughtExceptions(this), true);
    processesPool = new ForkJoinPool(VmSettings.NUM_THREADS,
        new ProcessThreadFactory(), new UncaughtExceptions(this), true);
    forkJoinPool = new ForkJoinPool(VmSettings.NUM_THREADS,
        new ForkJoinThreadFactory(), new UncaughtExceptions(this), false);
    threadPool = new ForkJoinPool(MAX_THREADS,
        new ForkJoinThreadFactory(), new UncaughtExceptions(this), false);
  }

  /**
   * Used by language server.
   */
  public VM(final VmOptions vmOptions) {
    this.avoidExitForTesting = true;
    this.options = vmOptions;

    actorPool = null;
    processesPool = null;
    forkJoinPool = null;
    threadPool = null;
  }

  public WebDebugger getWebDebugger() {
    return webDebugger;
  }

  public Breakpoints getBreakpoints() {
    return webDebugger.getBreakpoints();
  }

  private final Map<String, Object> exports = new HashMap<>();

  @TruffleBoundary
  public boolean registerExport(final String name, final Object value) {
    boolean wasExportedAlready = exports.containsKey(name);
    exports.put(name, value);
    return wasExportedAlready;
  }

  @TruffleBoundary
  public Object getExport(final String name) {
    return exports.get(name);
  }

  public SObjectWithoutFields getVmMirror() {
    return vmMirror;
  }

  /**
   * Used by language server.
   */
  public SomLanguage getLanguage() {
    return language;
  }

  /**
   * Used in {@link som.tests.BasicInterpreterTests} to identify which basic test method to
   * invoke.
   */
  public String getTestSelector() {
    return options.testSelector;
  }

  public Primitives getPrimitives() {
    return objectSystem.getPrimitives();
  }

  public InlinableNodes<SSymbol> getInlinableNodes() {
    return objectSystem.getInlinableNodes();
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

  private static final Object dynamicInstrumentationLock = new Object();

  public static void insertInstrumentationWrapper(final Node node) {

    // TODO: make thread-safe!!!
    // TODO: can I assert that it is locked?? helper on Node??
    if (VmSettings.INSTRUMENTATION) {
      assert node.getSourceSection() != null
          || (node instanceof WrapperNode) : "Node needs source section, or needs to be wrapper";
      synchronized (dynamicInstrumentationLock) {
        InstrumentationHandler.insertInstrumentationWrapper(node);
      }
    }
  }

  private static void outputToIGV(final Method method) {
    GraphPrintVisitor graphPrinter = new GraphPrintVisitor();

    graphPrinter.beginGraph(method.toString()).visit(method);

    graphPrinter.printToNetwork(true);
    graphPrinter.close();
  }

  public ForkJoinPool getActorPool() {
    return actorPool;
  }

  public ForkJoinPool getProcessPool() {
    return processesPool;
  }

  public ForkJoinPool getForkJoinPool() {
    return forkJoinPool;
  }

  public ForkJoinPool getThreadPool() {
    return threadPool;
  }

  /**
   * @return true, if there are no scheduled submissions,
   *         and no active threads in the pool, false otherwise.
   *         This is only best effort, it does not look at the actor's
   *         message queues.
   */
  public boolean isPoolIdle() {
    // TODO: this is not working when a thread blocks, then it seems
    // not to be considered running
    return actorPool.isQuiescent() && processesPool.isQuiescent()
        && forkJoinPool.isQuiescent() && threadPool.isQuiescent();
  }

  public void reportSyntaxElement(final Class<? extends Tags> type,
      final SourceSection source) {
    if (webDebugger != null) {
      webDebugger.reportSyntaxElement(type, source);
    }
  }

  public void reportParsedRootNode(final Method rootNode) {
    if (webDebugger != null) {
      webDebugger.reportRootNodeAfterParsing(rootNode);
    }

    if (VmSettings.IGV_DUMP_AFTER_PARSING) {
      outputToIGV(rootNode);
    }
  }

  public void reportLoadedSource(final Source source) {
    if (webDebugger != null) {
      webDebugger.reportLoadedSource(source);
    }
  }

  public boolean shouldExit() {
    return shouldExit;
  }

  public int lastExitCode() {
    return lastExitCode;
  }

  public Object[] getArguments() {
    return options.args;
  }

  private void shutdownPools() {
    ForkJoinPool[] pools =
        new ForkJoinPool[] {actorPool, processesPool, forkJoinPool, threadPool};

    for (ForkJoinPool pool : pools) {
      pool.shutdown();
      try {
        pool.awaitTermination(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Does minimal cleanup and disposes the polyglot engine, before doing a hard
   * exit. This method is expected to be called from main thread.
   */
  @TruffleBoundary
  public void shutdownAndExit(final int errorCode) {
    if (truffleProfiler != null) {
      truffleProfiler.printHistograms(System.err);
    }

    shutdownPools();

    TracingBackend.waitForTrace();

    int code = errorCode;
    if (TracingActors.ReplayActor.printMissingMessages() && errorCode == 0) {
      code = 1;
    }
    engine.dispose();
    if (VmSettings.MEMORY_TRACING) {
      TracingBackend.reportPeakMemoryUsage();
    }
    System.exit(code);
  }

  /**
   * Request a shutdown and exit from the VM. This does not happen immediately.
   * Instead, we instruct the main thread to do it, and merely kill the current
   * thread.
   *
   * @param errorCode to be returned as exit code from the program
   */
  public void requestExit(final int errorCode) {
    TruffleCompiler.transferToInterpreter("exit");
    lastExitCode = errorCode;
    shouldExit = true;
    objectSystem.releaseMainThread(errorCode);

    throw new ThreadDeath();
  }

  public void errorExit(final String message) {
    TruffleCompiler.transferToInterpreter("errorExit");
    Output.errorPrintln("Run-time Error: " + message);
    requestExit(1);
  }

  public boolean isAvoidingExit() {
    return avoidExitForTesting;
  }

  public void initalize(final SomLanguage lang) throws IOException {
    Actor.initializeActorSystem(language);

    assert objectSystem == null;
    objectSystem = new ObjectSystem(new SourcecodeCompiler(lang), structuralProbe, this);
    objectSystem.loadKernelAndPlatform(options.platformFile, options.kernelFile);

    assert vmMirror == null : "VM seems to be initialized already";
    assert mainActor == null : "VM seems to be initialized already";

    mainActor = Actor.createActor(this);
    vmMirror = objectSystem.initialize();

    if (VmSettings.ACTOR_TRACING || VmSettings.MEDEOR_TRACING) {
      TracingBackend.startTracingBackend();
    }
    if (VmSettings.MEDEOR_TRACING) {
      MedeorTrace.recordMainActor(mainActor, objectSystem);
    }

    language = lang;
  }

  public Object execute(final String selector) {
    return objectSystem.execute(selector);
  }

  public void execute() {
    objectSystem.executeApplication(vmMirror, mainActor);
  }

  public static void main(final String[] args) {
    VmOptions vmOptions = new VmOptions(args);

    if (!vmOptions.configUsable()) {
      return;
    }

    VM vm = new VM(vmOptions, false);
    Builder builder = vm.createPolyglotBuilder();

    vm.startExecution(builder);
  }

  public Builder createPolyglotBuilder() {
    Builder builder = PolyglotEngine.newBuilder();
    builder.config(SomLanguage.MIME_TYPE, SomLanguage.VM_OBJECT, this);
    return builder;
  }

  private void startExecution(final Builder builder) {
    engine = builder.build();

    Map<String, ? extends Instrument> instruments = engine.getRuntime().getInstruments();

    if (options.profilingEnabled) {
      Instrument profiler = instruments.get(ProfilerInstrument.ID);
      if (profiler == null) {
        Output.errorPrintln("Truffle profiler not available. Might be a class path issue");
      } else {
        profiler.setEnabled(options.profilingEnabled);
        truffleProfiler = Profiler.find(engine);
        truffleProfiler.setCollecting(true);
        truffleProfiler.setTiming(true);
      }
    }

    Debugger debugger = null;
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      debugger = Debugger.find(engine);
    }

    if (options.webDebuggerEnabled) {
      assert VmSettings.TRUFFLE_DEBUGGER_ENABLED && debugger != null;
      Instrument webDebuggerInst = instruments.get(WebDebugger.ID);
      webDebuggerInst.setEnabled(true);

      webDebugger = webDebuggerInst.lookup(WebDebugger.class);
      webDebugger.startServer(debugger, this);
    }

    if (options.coverageEnabled) {
      Instrument coveralls = instruments.get(Coverage.ID);
      coveralls.setEnabled(true);
      Coverage cov = coveralls.lookup(Coverage.class);
      try {
        cov.setOutputFile(options.coverageFile);
      } catch (IOException e) {
        Output.errorPrint("Failed to setup coverage tracking: " + e.getMessage());
      }
    }

    if (options.dynamicMetricsEnabled) {
      assert VmSettings.DYNAMIC_METRICS;
      Instrument dynM = instruments.get(DynamicMetrics.ID);
      dynM.setEnabled(true);
      structuralProbe = dynM.lookup(StructuralProbe.class);
      assert structuralProbe != null : "Initialization of DynamicMetrics tool incomplete";
    }

    if (options.siCandidateIdentifierEnabled) {
      assert !options.dynamicMetricsEnabled : "Currently, DynamicMetrics and CandidateIdentifer are not compatible";
      Instrument siCI = instruments.get(CandidateIdentifier.ID);
      siCI.setEnabled(true);
      structuralProbe = siCI.lookup(StructuralProbe.class);
      assert structuralProbe != null : "Initialization of CandidateIdentifer tool incomplete";
    }

    Value returnCode = engine.eval(SomLanguage.START);
    shutdownAndExit(returnCode.as(Integer.class));
  }

  public SClass loadExtensionModule(final String filename) {
    return objectSystem.loadExtensionModule(filename);
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

    ThreadingModule.ThreadingModule = null;
    ThreadingModule.ThreadClass = null;
    ThreadingModule.ThreadClassId = null;
    ThreadingModule.TaskClass = null;
    ThreadingModule.TaskClassId = null;
    ThreadingModule.MutexClass = null;
    ThreadingModule.MutexClassId = null;
    ThreadingModule.ConditionClass = null;
    ThreadingModule.ConditionClassId = null;

    ChannelPrimitives.resetClassReferences();
  }
}
