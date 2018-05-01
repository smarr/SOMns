package tools.concurrency;

import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerDirectives;

import som.vm.VmSettings;
import sun.misc.Unsafe;
import tools.concurrency.ActorExecutionTrace.ActorTraceBuffer;
import tools.concurrency.nodes.TraceActorContextNode;


public abstract class TraceBuffer {

  public static TraceBuffer create(final long implThreadId) {
    assert VmSettings.ACTOR_TRACING || VmSettings.MEDEOR_TRACING;
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      return MedeorTrace.MedeorTraceBuffer.create(implThreadId);
    } else {
      return new ActorTraceBuffer();
    }
  }

  static final Unsafe UNSAFE;
  static final long   BYTE_ARR_BASE_OFFSET;

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

  protected byte[] buffer;
  protected int    position;

  protected TraceBuffer() {
    buffer = TracingBackend.getEmptyBuffer();
  }

  public int position() {
    assert position <= TracingBackend.BUFFER_SIZE;
    assert position <= buffer.length;
    return position;
  }

  public void position(final int newPosition) {
    assert newPosition >= 0;
    assert newPosition <= TracingBackend.BUFFER_SIZE;
    assert newPosition <= buffer.length;
    if (newPosition < 0) {
      CompilerDirectives.transferToInterpreter();
      throw new IllegalArgumentException();
    }
    position = newPosition;
  }

  private int nextPutIndex() {
    assert position + 1 <= TracingBackend.BUFFER_SIZE;
    assert position + 1 <= buffer.length;
    return position++;

  }

  private int nextPutIndex(final int nb) {
    assert position + nb <= TracingBackend.BUFFER_SIZE;
    assert position + nb <= buffer.length;
    int p = position;
    position += nb;
    return p;
  }

  public final void returnBuffer(final byte[] nextBuffer) {
    TracingBackend.returnBuffer(buffer, position);
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
    assert (position == TracingBackend.BUFFER_SIZE) == ((buffer.length - position) == 0);
    return position == TracingBackend.BUFFER_SIZE;
  }

  public final boolean ensureSufficientSpace(final int requiredSpace,
      final TraceActorContextNode tracer) {
    if (position + requiredSpace >= TracingBackend.BUFFER_SIZE) {
      swapBufferWhenNotEnoughSpace(tracer);
      return true;
    }
    return false;
  }

  protected void swapBufferWhenNotEnoughSpace(final TraceActorContextNode tracer) {
    swapStorage();
  }

  public void putByteAt(final int idx, final byte x) {
    assert buffer.length >= TracingBackend.BUFFER_SIZE;
    assert 0 <= idx && idx < TracingBackend.BUFFER_SIZE;
    UNSAFE.putByte(buffer, BYTE_ARR_BASE_OFFSET + idx, x);
  }

  public void putShortAt(final int idx, final short x) {
    assert buffer.length >= TracingBackend.BUFFER_SIZE;
    assert 0 <= idx && (idx + 2) < TracingBackend.BUFFER_SIZE;
    UNSAFE.putShort(buffer, BYTE_ARR_BASE_OFFSET + idx, x);
  }

  public void putIntAt(final int idx, final int x) {
    assert buffer.length >= TracingBackend.BUFFER_SIZE;
    assert 0 <= idx && (idx + 4) < TracingBackend.BUFFER_SIZE;
    UNSAFE.putInt(buffer, BYTE_ARR_BASE_OFFSET + idx, x);
  }

  public void putByteShortAt(final int idx, final byte a, final short b) {
    assert buffer.length >= TracingBackend.BUFFER_SIZE;
    assert 0 <= idx && (idx + 1 + 2) < TracingBackend.BUFFER_SIZE;
    UNSAFE.putByte(buffer, BYTE_ARR_BASE_OFFSET + idx, a);
    UNSAFE.putShort(buffer, BYTE_ARR_BASE_OFFSET + idx + 1, b);
  }

  protected final void put(final byte x) {
    assert buffer.length >= TracingBackend.BUFFER_SIZE;
    assert 0 <= position && (position + 1) < TracingBackend.BUFFER_SIZE;
    UNSAFE.putByte(buffer, BYTE_ARR_BASE_OFFSET + nextPutIndex(), x);
  }

  public void putByteInt(final byte a, final int b) {
    int bi = nextPutIndex(1 + 4);
    putByteAt(bi, a);
    assert buffer.length >= TracingBackend.BUFFER_SIZE;
    assert (0 <= bi + 1) && (bi + 1 + 4) < TracingBackend.BUFFER_SIZE;
    UNSAFE.putInt(buffer, BYTE_ARR_BASE_OFFSET + bi + 1, b);
  }

  protected final void putShort(final short x) {
    int bi = nextPutIndex(2);
    assert buffer.length >= TracingBackend.BUFFER_SIZE;
    assert 0 <= bi && (bi + 2) < TracingBackend.BUFFER_SIZE;
    UNSAFE.putShort(buffer, BYTE_ARR_BASE_OFFSET + bi, x);
  }

  protected final void putInt(final int x) {
    int bi = nextPutIndex(4);
    assert buffer.length >= TracingBackend.BUFFER_SIZE;
    assert 0 <= bi && (bi + 4) < TracingBackend.BUFFER_SIZE;
    UNSAFE.putInt(buffer, BYTE_ARR_BASE_OFFSET + bi, x);
  }

  protected final void putLong(final long x) {
    int bi = nextPutIndex(8);
    assert buffer.length >= TracingBackend.BUFFER_SIZE;
    assert 0 <= bi && (bi + 8) < TracingBackend.BUFFER_SIZE;
    UNSAFE.putLong(buffer, BYTE_ARR_BASE_OFFSET + bi, x);
  }

  protected final void putDouble(final double x) {
    putLong(Double.doubleToRawLongBits(x));
  }
}
