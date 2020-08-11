package tools.replay;

/**
 * Types of elements in the trace, used to create a parse table.
 */
public enum TraceRecord {
  ACTIVITY_CREATION(0),
  ACTIVITY_CONTEXT(1),
  MESSAGE(2),
  PROMISE_MESSAGE(3),
  SYSTEM_CALL(4),
  CHANNEL_READ(5),
  CHANNEL_WRITE(6),
  LOCK_ISLOCKED(7),
  LOCK_LOCK(8),
  CONDITION_WAKEUP(9),
  CONDITION_TIMEOUT(10),
  PROMISE_RESOLUTION(11),
  PROMISE_CHAINED(12),
  PROMISE_RESOLUTION_END(13),
  TRANSACTION_COMMIT(14);

  public final byte value;

  TraceRecord(final int value) {
    this.value = (byte) value;
  }
}
