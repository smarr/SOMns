package tools.concurrency;

import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerDirectives;

import som.interpreter.actors.Actor.ActorProcessingThread;
import som.vm.VmSettings;
import sun.misc.Unsafe;
import tools.replay.actors.ActorExecutionTrace.ActorTraceBuffer;
import tools.replay.nodes.TraceContextNode;


public abstract class TraceBuffer {

  public static TraceBuffer create(final long threadId) {
    assert VmSettings.ACTOR_TRACING || VmSettings.KOMPOS_TRACING;
    if (VmSettings.KOMPOS_TRACING) {
      return new KomposTrace.KomposTraceBuffer(threadId);
    } else {
      return new ActorTraceBuffer();
    }
  }

  public static final Unsafe UNSAFE;
  public static final long   BYTE_ARR_BASE_OFFSET;

  private static Unsafe loadUnsafe() {
    try {
      return Unsafe.getUnsafe();
    } catch (SecurityException e) {
      // can fail, is ok, just to the fallback below
    }
    try {
      Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
      theUnsafeInstance.setAccessible(true);
      return (Unsafe) theUnsafeInstance.get(Unsafe.class);
    } catch (Exception e) {
      throw new RuntimeException(
          "exception while trying to get Unsafe.theUnsafe via reflection:", e);
    }
  }

  static {
    UNSAFE = loadUnsafe();
    BYTE_ARR_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
    assert UNSAFE.arrayIndexScale(
        byte[].class) == 1 : "Expect byte elements to be exactly one byte in size.";
  }

  protected byte[]  buffer;
  protected int     position;
  private final int bufferSize;

  protected TraceBuffer() {
    buffer = TracingBackend.getEmptyBuffer();
    this.bufferSize = VmSettings.BUFFER_SIZE;
  }

  protected TraceBuffer(final boolean create) {
    if (create) {
      this.buffer = new byte[VmSettings.BUFFER_SIZE];
    } else {
      buffer = TracingBackend.getEmptyBuffer();
    }
    this.bufferSize = VmSettings.BUFFER_SIZE;
  }

  protected TraceBuffer(final int size) {
    this.buffer = new byte[size];
    this.bufferSize = size;
  }

  public int position() {
    assert position <= bufferSize;
    assert position <= buffer.length;
    return position;
  }

  public void position(final int newPosition) {
    assert newPosition >= 0;
    assert newPosition <= bufferSize;
    assert newPosition <= buffer.length;
    if (newPosition < 0) {
      CompilerDirectives.transferToInterpreter();
      throw new IllegalArgumentException();
    }
    position = newPosition;
  }

  private int nextPutIndex() {
    assert position + 1 <= bufferSize;
    assert position + 1 <= buffer.length;
    return position++;

  }

  private int nextPutIndex(final int nb) {
    assert position + nb <= bufferSize;
    assert position + nb <= buffer.length;
    int p = position;
    position += nb;
    return p;
  }

  public final void returnBuffer(final byte[] nextBuffer) {
    if (VmSettings.SNAPSHOTS_ENABLED) {
      TracingBackend.returnBuffer(buffer, position,
          ActorProcessingThread.currentThread().getSnapshotId());
    } else {
      TracingBackend.returnBuffer(buffer, position);
    }
    buffer = nextBuffer;
    position = 0;
  }

  public final void swapStorage() {
    returnBuffer(TracingBackend.getEmptyBuffer());
  }

  public boolean isEmpty() {
    return position == 0;
  }

  public boolean isFull() {
    assert (position == bufferSize) == ((buffer.length - position) == 0);
    return position == bufferSize;
  }

  public final boolean ensureSufficientSpace(final int requiredSpace,
      final TraceContextNode tracer) {
    if (position + requiredSpace >= bufferSize) {
      swapBufferWhenNotEnoughSpace(tracer);
      return true;
    }
    return false;
  }

  protected void swapBufferWhenNotEnoughSpace(final TraceContextNode tracer) {
    swapStorage();
  }

  public void putByteAt(final int idx, final byte x) {
    assert buffer.length >= bufferSize;
    assert 0 <= idx && idx < bufferSize;
    UNSAFE.putByte(buffer, BYTE_ARR_BASE_OFFSET + idx, x);
  }

  public void putShortAt(final int idx, final short x) {
    assert buffer.length >= bufferSize;
    assert 0 <= idx && (idx + 2) < bufferSize;
    UNSAFE.putShort(buffer, BYTE_ARR_BASE_OFFSET + idx, x);
  }

  public void putIntAt(final int idx, final int x) {
    assert buffer.length >= bufferSize;
    assert 0 <= idx && (idx + 4) < bufferSize;
    UNSAFE.putInt(buffer, BYTE_ARR_BASE_OFFSET + idx, x);
  }

  public void putLongAt(final int idx, final long x) {
    assert buffer.length >= bufferSize;
    assert 0 <= idx && (idx + 8) < bufferSize;
    UNSAFE.putLong(buffer, BYTE_ARR_BASE_OFFSET + idx, x);
  }

  public void putDoubleAt(final int idx, final double x) {
    assert buffer.length >= bufferSize;
    assert 0 <= idx && (idx + 8) < bufferSize;
    UNSAFE.putDouble(buffer, BYTE_ARR_BASE_OFFSET + idx, x);
  }

  public void putByteShortAt(final int idx, final byte a, final short b) {
    assert buffer.length >= bufferSize;
    assert 0 <= idx && (idx + 1 + 2) < bufferSize;
    UNSAFE.putByte(buffer, BYTE_ARR_BASE_OFFSET + idx, a);
    UNSAFE.putShort(buffer, BYTE_ARR_BASE_OFFSET + idx + 1, b);
  }

  protected final void put(final byte x) {
    assert buffer.length >= bufferSize;
    assert 0 <= position && (position + 1) < bufferSize;
    UNSAFE.putByte(buffer, BYTE_ARR_BASE_OFFSET + nextPutIndex(), x);
  }

  public void putByteInt(final byte a, final int b) {
    int bi = nextPutIndex(1 + 4);
    putByteAt(bi, a);
    assert buffer.length >= bufferSize;
    assert (0 <= bi + 1) && (bi + 1 + 4) < bufferSize;
    UNSAFE.putInt(buffer, BYTE_ARR_BASE_OFFSET + bi + 1, b);
  }

  protected final void putShort(final short x) {
    int bi = nextPutIndex(2);
    assert buffer.length >= bufferSize;
    assert 0 <= bi && (bi + 2) < bufferSize;
    UNSAFE.putShort(buffer, BYTE_ARR_BASE_OFFSET + bi, x);
  }

  protected final void putInt(final int x) {
    int bi = nextPutIndex(4);
    assert buffer.length >= bufferSize;
    assert 0 <= bi && (bi + 4) < bufferSize;
    UNSAFE.putInt(buffer, BYTE_ARR_BASE_OFFSET + bi, x);
  }

  protected final void putLong(final long x) {
    int bi = nextPutIndex(8);
    assert buffer.length >= bufferSize;
    assert 0 <= bi && (bi + 8) < bufferSize;
    UNSAFE.putLong(buffer, BYTE_ARR_BASE_OFFSET + bi, x);
  }

  protected final void putDouble(final double x) {
    putLong(Double.doubleToRawLongBits(x));
  }

  public void putBytesAt(final int idx, final byte[] bytes) {
    assert buffer.length >= bufferSize;
    assert 0 <= idx && (idx + bytes.length) < bufferSize;
    UNSAFE.copyMemory(bytes, BYTE_ARR_BASE_OFFSET, buffer, BYTE_ARR_BASE_OFFSET + idx,
        bytes.length);
  }
}
