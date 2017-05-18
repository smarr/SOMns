package tools;

import som.vm.VmSettings;

/**
 * Characterize and document some of the trace data.
 */
public class TraceData {

  public static final int SOURCE_SECTION_SIZE = VmSettings.TRUFFLE_DEBUGGER_ENABLED ? 8 : 0;

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
