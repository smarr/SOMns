package tools.replay;

/**
 * Types of elements in the trace, used to create a parse table.
 */
public enum TraceRecord {
  ACTOR_CREATION(0),
  ACTOR_CONTEXT(1),
  MESSAGE(2),
  PROMISE_MESSAGE(3),
  SYSTEM_CALL(4),
  CHANNEL_CREATION(5),
  PROCESS_CONTEXT(6),
  CHANNEL_READ(7),
  CHANNEL_WRITE(8),
  PROCESS_CREATION(9),
  LOCK_ISLOCKED(10),
  LOCK_LOCK(11),
  CONDITION_WAKEUP(12),
  CONDITION_SIGNALALL(13),
  CONDITION_WAIT(14),
  CONDITION_AWAITTIMEOUT(15),
  LOCK_CREATE(16),
  CONDITION_CREATE(17),
  CONDITION_AWAITTIMEOUT_RES(18);

  public final byte value;

  TraceRecord(final int value) {
    this.value = (byte) value;
  }

  public static final byte EXTERNAL_BIT = 16;
}
