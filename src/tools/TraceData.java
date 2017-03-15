package tools;


/**
 * Characterize and document some of the trace data.
 */
public class TraceData {
  /**
   * EventIds for custom events must not exceed 7 bits (unsigned).
   */
  public static final byte ACTOR_CREATION     = 1;
  public static final byte PROMISE_CREATION   = 2;
  public static final byte PROMISE_RESOLUTION = 3;
  public static final byte PROMISE_CHAINED    = 4;
  public static final byte MAILBOX            = 5;
  public static final byte THREAD             = 6;
  public static final byte MAILBOX_CONTD      = 7;

  public static final byte ACTOR_CREATION_ORIGIN     = 8;

  public static final byte PROCESS_CREATION   = 10;
  public static final byte PROCESS_COMPLETION = 11;

  public static final byte TASK_SPAWN = 12;
  public static final byte TASK_JOIN  = 13;

  public static final byte PROMISE_ERROR = 14;

  public static final byte PROCESS_CREATION_ORIGIN   = 14;
  public static final byte TASK_SPAWN_ORIGIN = 15;

  /**
   * Messages use a different EventId system, the most significant bit is 1 to clearly distinguish it from custom Events.
   * The other bits are used to encode what information is contained in that event.
   */
  public static final byte MESSAGE_BIT  = (byte) 0x80;
  public static final byte PROMISE_BIT   = 0x40;
  public static final byte TIMESTAMP_BIT = 0x20;
  public static final byte PARAMETER_BIT = 0x10;


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
