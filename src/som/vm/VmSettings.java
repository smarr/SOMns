package som.vm;

import com.oracle.truffle.api.TruffleOptions;

import bd.settings.Settings;


public class VmSettings implements Settings {
  public static final int NUM_THREADS;

  // TODO: revise naming of flags
  public static final boolean FAIL_ON_MISSING_OPTIMIZATIONS;
  public static final boolean DEBUG_MODE;
  public static final boolean UNIFORM_TRACING;
  public static final boolean MEMORY_TRACING;
  public static final String  TRACE_FILE;
  public static final boolean DISABLE_TRACE_FILE;
  public static final boolean INSTRUMENTATION;
  public static final boolean DYNAMIC_METRICS;
  public static final boolean DNU_PRINT_STACK_TRACE;
  public static final boolean REPLAY;
  public static final boolean KOMPOS_TRACING;
  public static final boolean ASSISTED_DEBUGGING;

  public static final boolean TRACE_SMALL_IDS;
  public static final boolean SNAPSHOTS_ENABLED;
  public static final boolean TRACK_SNAPSHOT_ENTITIES;
  public static final boolean TEST_SNAPSHOTS;
  public static final boolean TEST_SERIALIZE_ALL;

  public static final boolean TRUFFLE_DEBUGGER_ENABLED;

  public static final boolean ANSI_COLOR_IN_OUTPUT;

  public static final String INSTRUMENTATION_PROP = "som.instrumentation";

  public static final int     BUFFERS_PER_THREAD;
  public static final int     BUFFER_SIZE;
  public static final int     ASSISTED_DEBUGGING_BREAKPOINTS;
  public static final boolean RECYCLE_BUFFERS;
  public static final int     BUFFER_TIMEOUT;

  public static final boolean SENDER_SIDE_TRACING;
  public static final boolean RECEIVER_SIDE_TRACING;

  public static final boolean SENDER_SIDE_REPLAY;
  public static final boolean RECEIVER_SIDE_REPLAY;

  public static final boolean ACTOR_ASYNC_STACK_TRACE_STRUCTURE;
  public static final boolean ACTOR_ASYNC_STACK_TRACE_METHOD_CACHE;
  public static final boolean ACTOR_ASYNC_STACK_TRACE_INLINE_CACHE;

  public static final String BASE_DIRECTORY;

  public static final boolean USE_PINNING;

  static {
    String prop = System.getProperty("som.threads");
    if (prop == null) {
      NUM_THREADS = Runtime.getRuntime().availableProcessors();
    } else {
      NUM_THREADS = Integer.valueOf(prop);
    }

    FAIL_ON_MISSING_OPTIMIZATIONS = getBool("som.failOnMissingOptimization", false);
    DEBUG_MODE = getBool("som.debugMode", false);
    TRUFFLE_DEBUGGER_ENABLED = getBool("som.truffleDebugger", false);

    TRACE_FILE =
        System.getProperty("som.traceFile", System.getProperty("user.dir") + "/traces/trace");
    MEMORY_TRACING = getBool("som.memoryTracing", false);
    REPLAY = getBool("som.replay", false);
    KOMPOS_TRACING = (getBool("som.komposTracing", false) || TRUFFLE_DEBUGGER_ENABLED)
        && !TruffleOptions.AOT; // REPLAY;
    ASSISTED_DEBUGGING = getBool("som.assistedDebugging", false) && KOMPOS_TRACING;
    DISABLE_TRACE_FILE = getBool("som.disableTraceFile", false) || (REPLAY && !KOMPOS_TRACING);
    TRACE_SMALL_IDS = getBool("som.smallIds", false);

    UNIFORM_TRACING = getBool("som.actorTracing", false) && !TruffleOptions.AOT;
    boolean receiverSide = getBool("som.actorReceiverTracing", false);
    SENDER_SIDE_TRACING = UNIFORM_TRACING && !receiverSide;
    RECEIVER_SIDE_TRACING = UNIFORM_TRACING && receiverSide;

    SENDER_SIDE_REPLAY = REPLAY && !receiverSide;
    RECEIVER_SIDE_REPLAY = REPLAY && receiverSide;

    ACTOR_ASYNC_STACK_TRACE_STRUCTURE = getBool("som.actorAsyncStackTraceStructure", false);
    ACTOR_ASYNC_STACK_TRACE_METHOD_CACHE =
        getBool("som.actorAsyncStackTraceMethodCache", false);
    ACTOR_ASYNC_STACK_TRACE_INLINE_CACHE =
        getBool("som.actorAsyncStackTraceInlineCache", false);

    TEST_SNAPSHOTS = getBool("som.snapshotTest", false);
    TEST_SERIALIZE_ALL = getBool("som.actorSnapshotAll", false);
    SNAPSHOTS_ENABLED = getBool("som.actorSnapshot", false) || TEST_SNAPSHOTS;
    TRACK_SNAPSHOT_ENTITIES = (REPLAY && SNAPSHOTS_ENABLED) || TEST_SNAPSHOTS;

    boolean dm = getBool("som.dynamicMetrics", false);
    DYNAMIC_METRICS = dm;
    INSTRUMENTATION = dm || getBool(INSTRUMENTATION_PROP, false);

    DNU_PRINT_STACK_TRACE = getBool("som.printStackTraceOnDNU", false);
    ANSI_COLOR_IN_OUTPUT = getBool("som.useAnsiColoring", false);

    ASSISTED_DEBUGGING_BREAKPOINTS = getInteger("som.assistedDebuggingBp", -1);
    BUFFER_SIZE = getInteger("som.buffSize", 1024 * 1024);
    BUFFERS_PER_THREAD = getInteger("som.buffPerThread", 4);
    BUFFER_TIMEOUT = getInteger("som.buffDelay", 50);
    RECYCLE_BUFFERS = getBool("som.bufferRecycling", true);

    BASE_DIRECTORY = System.getProperty("som.baseDir", System.getProperty("user.dir"));

    String osName = System.getProperty("os.name", "generic").toLowerCase();
    boolean isLinux = osName.contains("linux");
    USE_PINNING = getBool("som.usePinning", true) && isLinux && !TruffleOptions.AOT;
  }

  private static boolean getBool(final String prop, final boolean defaultVal) {
    return Boolean.parseBoolean(System.getProperty(prop, defaultVal ? "true" : "false"));
  }

  private static int getInteger(final String prop, final int defaultVal) {
    return Integer.parseInt(System.getProperty(prop, "" + defaultVal));
  }

  @Override
  public boolean dynamicMetricsEnabled() {
    return DYNAMIC_METRICS;
  }
}
