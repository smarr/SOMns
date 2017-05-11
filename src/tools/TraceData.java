package tools;

import tools.debugger.entities.EntityType;

/**
 * Characterize and document some of the trace data.
 */
public class TraceData {
  /**
   * EventIds for custom events must not exceed 7 bits (unsigned).
   * The following values are derived from {@link EntityType}.
   */
  public static final byte PROCESS_CREATION     = 1;
  public static final byte PROCESS_COMPLETION   = 2;
  public static final byte ACTOR_CREATION       = 7;
  public static final byte TASK_SPAWN           = 15;
  public static final byte THREAD_SPAWN         = 17;

  public static final byte TURN_START         = 13;
  public static final byte TURN_END           = 14;
  public static final byte TRANSACTION_START  = 21;
  public static final byte TRANSACTION_END    = 22;
  public static final byte MONITOR_ENTER      = 23;
  public static final byte MONITOR_EXIT       = 24;

  public static final byte CHANNEL_CREATION     = 3;



  public static final byte CHANNEL_MESSAGE_SEND = 5;
  public static final byte CHANNEL_MESSAGE_RCV  = 6;


  public static final byte ACTOR_MSG_SEND     = 11;


  public static final byte TASK_JOIN          = 16;
  public static final byte THREAD_JOIN        = 18;


  public static final byte IMPL_THREAD        = 25;

  /** Marks the trace entry to encode currently executing activity. */
  public static final byte IMPL_THREAD_CURRENT_ACTIVITY = 26;

  // Note, the following constants are manually defined, based on the assumption
  // that EntityType creation/completion is not > 32

  public static final byte PROMISE_RESOLUTION = 33;
  public static final byte PROMISE_CHAINED    = 34;
  public static final byte PROMISE_ERROR      = 35;


  public static final long ACTIVITY_ID_BITS = 30;
  public static final long THREAD_ID_BITS   = 10;

  /**
   * For some value, for instance stack frame id, object id, etc.
   */
  public static final long VAL_ID_BITS = 13;

  private static final long JAVA_SCRIPT_INT_BITS = 53;
  private static final long SAFE_JS_INT_VAL = (1L << JAVA_SCRIPT_INT_BITS) - 1;

  private static final long MAX_SAFE_ACTIVITY_ID = (1L << (ACTIVITY_ID_BITS + THREAD_ID_BITS)) - 1;
  private static final long  MAX_SAFE_VAL_ID = (1L << VAL_ID_BITS) - 1;

  private static final long VAL_ID_MASK = MAX_SAFE_VAL_ID;

  /**
   * Check whether the value fits within the 53bit ints we have in JS.
   */
  public static boolean isWithinJSIntValueRange(final long val) {
    return val < SAFE_JS_INT_VAL && val > -SAFE_JS_INT_VAL;
  }

  public static long makeGlobalId(final int valId, final long activityId) {
    assert activityId <= MAX_SAFE_ACTIVITY_ID && activityId >= 0;
    assert      valId <= MAX_SAFE_VAL_ID      && valId >= 0;

    return (activityId << VAL_ID_BITS) + valId;
  }

  public static int valIdFromGlobal(final long globalId) {
    return (int) (globalId & VAL_ID_MASK);
  }

  public static long getActivityIdFromGlobalValId(final long globalValId) {
    return globalValId >> VAL_ID_BITS;
  }

  static {
    // For easy interoperability, we need to ensure that we only use that many bits
    assert ACTIVITY_ID_BITS + THREAD_ID_BITS + VAL_ID_BITS <= JAVA_SCRIPT_INT_BITS;
  }
}
