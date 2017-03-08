package tools;


public class TraceData {
  public static final byte MESSAGE_BASE = (byte) 0x80;
  public static final byte PROMISE_BIT = 0x40;
  public static final byte TIMESTAMP_BIT = 0x20;
  public static final byte PARAMETER_BIT = 0x10;

  /** Used to shift the thread id to the 8 most significant bits. */
  public static final int ACTIVITY_ID_BITS = 56;
}
