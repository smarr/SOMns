package tools.debugger.entities;

/**
 * Define Marker Constants, are used by Trace Parser.
 */
public class Marker {

  public static final byte PROCESS_CREATION   = 1;
  public static final byte PROCESS_COMPLETION = 2;
  public static final byte ACTOR_CREATION     = 3;
  public static final byte TASK_SPAWN         = 4;
  public static final byte THREAD_SPAWN       = 5;

  public static final byte TURN_START        = 6;
  public static final byte TURN_END          = 7;
  public static final byte MONITOR_ENTER     = 8;
  public static final byte MONITOR_EXIT      = 9;
  public static final byte TRANSACTION_START = 10;
  public static final byte TRANSACTION_END   = 11;

  public static final byte CHANNEL_CREATION = 12;
  public static final byte PROMISE_CREATION = 13;

  public static final byte ACTOR_MSG_SEND     = 14;
  public static final byte CHANNEL_MSG_SEND   = 15;
  public static final byte PROMISE_RESOLUTION = 16;

  public static final byte CHANNEL_MSG_RCV = 17;
  public static final byte TASK_JOIN       = 18;
  public static final byte THREAD_JOIN     = 19;

  public static final byte IMPL_THREAD                  = 20;
  public static final byte IMPL_THREAD_CURRENT_ACTIVITY = 21;

  public static final byte PROMISE_MSG_SEND = 22;

}
