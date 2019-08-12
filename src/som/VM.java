package som;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.GraphPrintVisitor;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.profiler.CPUSampler;

import bd.inlining.InlinableNodes;
import bd.tools.structure.StructuralProbe;
import coveralls.truffle.Coverage;
import som.compiler.MixinDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.compiler.SourcecodeCompiler;
import som.compiler.Variable;
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
import som.vm.NotYetImplementedException;
import som.vm.ObjectSystem;
import som.vm.Primitives;
import som.vm.VmOptions;
import som.vm.VmSettings;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import som.vmobjects.SSymbol;
import tools.concurrency.KomposTrace;
import tools.concurrency.TracingBackend;
import tools.debugger.WebDebugger;
import tools.debugger.session.Breakpoints;
import tools.dym.DynamicMetrics;
import tools.snapshot.SnapshotBackend;
import tools.superinstructions.CandidateIdentifier;


public final class VM {

  @CompilationFinal private StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> structuralProbe;

  @CompilationFinal private WebDebugger webDebugger;
  @CompilationFinal private CPUSampler  truffleProfiler;

  @CompilationFinal private TruffleContext context;

  private final ForkJoinPool actorPool;
  private final ForkJoinPool forkJoinPool;
  private final ForkJoinPool processesPool;
  private final ForkJoinPool threadPool;

  @CompilationFinal private ObjectSystem objectSystem;

  @CompilationFinal private SomLanguage language;

  private int              lastExitCode = 0;
  private volatile boolean shouldExit   = false;
  private final VmOptions  options;

  @CompilationFinal private SObjectWithoutFields vmMirror;
  @CompilationFinal private Actor                mainActor;

  private static final int MAX_THREADS = 0x7fff;

  public VM(final VmOptions vmOptions) {
    options = vmOptions;

    actorPool = new ForkJoinPool(VmSettings.NUM_THREADS,
        new ActorProcessingThreadFactory(this), new UncaughtExceptions(this), true);
    processesPool = new ForkJoinPool(VmSettings.NUM_THREADS,
        new ProcessThreadFactory(this), new UncaughtExceptions(this), true);
    forkJoinPool = new ForkJoinPool(VmSettings.NUM_THREADS,
        new ForkJoinThreadFactory(this), new UncaughtExceptions(this), false);
    threadPool = new ForkJoinPool(MAX_THREADS,
        new ForkJoinThreadFactory(this), new UncaughtExceptions(this), false);
  }

  /**
   * Used by language server.
   */
  public VM(final VmOptions vmOptions, final boolean languageServer) {
    assert languageServer : "Only to be used by language server";
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

  @SuppressWarnings("deprecation")
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

  public void reportSyntaxElement(final Class<? extends Tag> type,
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
      pool.shutdownNow();
      try {
        pool.awaitTermination(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Do some cleanup.
   */
  @TruffleBoundary
  public void shutdown() {
    if (truffleProfiler != null) {
      throw new NotYetImplementedException();
      // truffleProfiler.printHistograms(System.err);
    }

    shutdownPools();
  }

  public boolean isShutdown() {
    return actorPool.isShutdown();
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

  public void initalize(final SomLanguage lang) throws IOException {
    Actor.initializeActorSystem(language);

    assert objectSystem == null : "VM is reinitialized accidently? objectSystem is already set.";
    objectSystem = new ObjectSystem(new SourcecodeCompiler(lang), structuralProbe, this);
    objectSystem.loadKernelAndPlatform(options.platformFile, options.kernelFile);

    assert vmMirror == null : "VM seems to be initialized already";
    assert mainActor == null : "VM seems to be initialized already";

    mainActor = Actor.createActor(this);
    vmMirror = objectSystem.initialize();

    if (VmSettings.ACTOR_TRACING || VmSettings.KOMPOS_TRACING) {
      TracingBackend.startTracingBackend();
    }
    if (VmSettings.KOMPOS_TRACING) {
      KomposTrace.recordMainActor(mainActor, objectSystem);
    }

    language = lang;
  }

  public Object execute(final String selector) {
    return objectSystem.execute(selector);
  }

  public int execute() {
    return objectSystem.executeApplication(vmMirror, mainActor);
  }

  public Actor getMainActor() {
    return mainActor;
  }

  public void enterContext() {
    assert context != null : "setupInstruments(env) must have been called first";
    context.enter();
  }

  public void leaveContext() {
    assert context != null : "setupInstruments(env) must have been called first";
    context.leave(null);
  }

  /**
   * We only do this when we execute an application.
   * We don't setup the instruments for BasicInterpreterTests.
   */
  public void setupInstruments(final Env env) {
    context = env.getContext();

    Engine engine = Context.getCurrent().getEngine();

    if (options.profilingEnabled) {
      truffleProfiler = CPUSampler.find(engine);
      truffleProfiler.setCollecting(true);
    }

    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      assert options.webDebuggerEnabled : "If debugging is enabled, we currently expect the web debugger to be used.";
      Debugger debugger = Debugger.find(env);

      webDebugger = WebDebugger.find(env);
      webDebugger.startServer(debugger, this);
    }

    if (options.coverageEnabled) {
      Coverage cov = Coverage.find(env);
      try {
        cov.setOutputFile(options.coverageFile);
      } catch (IOException e) {
        Output.errorPrint("Failed to setup coverage tracking: " + e.getMessage());
      }
    }

    if (options.dynamicMetricsEnabled) {
      assert VmSettings.DYNAMIC_METRICS;
      structuralProbe = DynamicMetrics.find(engine);
      assert structuralProbe != null : "Initialization of DynamicMetrics tool incomplete";
    }

    if (options.siCandidateIdentifierEnabled) {
      assert !options.dynamicMetricsEnabled : "Currently, DynamicMetrics and CandidateIdentifer are not compatible";
      structuralProbe = CandidateIdentifier.find(env);
      assert structuralProbe != null : "Initialization of CandidateIdentifer tool incomplete";
    }

    if (VmSettings.TRACK_SNAPSHOT_ENTITIES) {
      assert !options.dynamicMetricsEnabled : "Currently, DynamicMetrics and Snapshots are not compatible";
      assert !options.siCandidateIdentifierEnabled : "Currently, CandidateIdentifer and Snapshots are not compatible";
      structuralProbe = SnapshotBackend.getProbe();
    }
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
