package tools.concurrency;

import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerDirectives;

import sun.misc.Unsafe;


public final class ByteBuffer {
  private static final Unsafe unsafe;
  private static final long   byteArrBaseOffset;

  static {
    unsafe = loadUnsafe();
    byteArrBaseOffset = unsafe.arrayBaseOffset(byte[].class);
    assert unsafe.arrayIndexScale(
        byte[].class) == 1 : "Expect byte elements to be exactly one byte in size.";
  }

  public static ByteBuffer allocate(final int capacity) {
    return new ByteBuffer(capacity);
  }

  private int position;

  private final byte[] buffer;

  private ByteBuffer(final int capacity) {
    buffer = new byte[capacity];
    this.position = 0;
  }

  public java.nio.ByteBuffer getReadingFromStartBuffer() {
    return java.nio.ByteBuffer.wrap(buffer, 0, position);
  }

  public int position() {
    return position;
  }

  public ByteBuffer position(final int newPosition) {
    if (newPosition < 0) {
      CompilerDirectives.transferToInterpreter();
      throw new IllegalArgumentException();
    }
    position = newPosition;
    return this;
  }

  public ByteBuffer rewind() {
    position = 0;
    return this;
  }

  public int remaining() {
    return buffer.length - position;
  }

  public ByteBuffer clear() {
    position = 0;
    return this;
  }

  private int nextPutIndex() {
    return position++;
  }

  private int nextPutIndex(final int nb) {
    int p = position;
    position += nb;
    return p;
  }

  private void _put(final int i, final byte b) {
    unsafe.putByte(buffer, byteArrBaseOffset + i, b);
  }

  private static byte short1(final short x) {
    return (byte) (x >> 8);
  }

  private static byte short0(final short x) {
    return (byte) (x);
  }

  private static byte long7(final long x) {
    return (byte) (x >> 56);
  }

  private static byte long6(final long x) {
    return (byte) (x >> 48);
  }

  private static byte long5(final long x) {
    return (byte) (x >> 40);
  }

  private static byte long4(final long x) {
    return (byte) (x >> 32);
  }

  private static byte long3(final long x) {
    return (byte) (x >> 24);
  }

  private static byte long2(final long x) {
    return (byte) (x >> 16);
  }

  private static byte long1(final long x) {
    return (byte) (x >> 8);
  }

  private static byte long0(final long x) {
    return (byte) (x);
  }

  private static byte int3(final int x) {
    return (byte) (x >> 24);
  }

  private static byte int2(final int x) {
    return (byte) (x >> 16);
  }

  private static byte int1(final int x) {
    return (byte) (x >> 8);
  }

  private static byte int0(final int x) {
    return (byte) (x);
  }

  public ByteBuffer putShort(final short x) {
    int bi = nextPutIndex(2);
    _put(bi, short1(x));
    _put(bi + 1, short0(x));

    return this;
  }

  public ByteBuffer putLong(final long x) {
    int bi = nextPutIndex(8);
    _put(bi, long7(x));
    _put(bi + 1, long6(x));
    _put(bi + 2, long5(x));
    _put(bi + 3, long4(x));
    _put(bi + 4, long3(x));
    _put(bi + 5, long2(x));
    _put(bi + 6, long1(x));
    _put(bi + 7, long0(x));

    return this;
  }

  public ByteBuffer putDouble(final double x) {
    putLong(Double.doubleToRawLongBits(x));
    return this;
  }

  public ByteBuffer putInt(final int x) {
    int bi = nextPutIndex(4);
    _put(bi, int3(x));
    _put(bi + 1, int2(x));
    _put(bi + 2, int1(x));
    _put(bi + 3, int0(x));

    return this;
  }

  public ByteBuffer put(final byte x) {
    unsafe.putByte(buffer, byteArrBaseOffset + nextPutIndex(), x);
    // buffer[nextPutIndex()] = x;
    return this;
  }

  public ByteBuffer put(final byte[] src) {
    return put(src, 0, src.length);
  }

  public ByteBuffer put(final byte[] src, final int offset, final int length) {
    System.arraycopy(src, offset, buffer, position, length);
    position += length;
    return this;
  }

  public void putByteByteShort(final byte a, final byte b, final short c) {
    int bi = nextPutIndex(1 + 2);
    _put(bi, a);
    _put(bi + 1, b);
    unsafe.putShort(buffer, byteArrBaseOffset + bi + 2, b);
  }

  public void putByteShort(final byte a, final short b) {
    int bi = nextPutIndex(1 + 2);
    _put(bi, a);
    unsafe.putShort(buffer, byteArrBaseOffset + bi + 1, b);
    // _put(bi + 1, short1(b));
    // _put(bi + 2, short0(b));
  }

  public void putByteInt(final byte a, final int b) {
    int bi = nextPutIndex(1 + 4);
    _put(bi, a);

    unsafe.putInt(buffer, byteArrBaseOffset + bi + 1, b);
    // _put(bi + 1, int3(b));
    // _put(bi + 2, int2(b));
    // _put(bi + 3, int1(b));
    // _put(bi + 4, int0(b));
  }

  public void putByteShortShort(final byte a, final short b, final short c) {
    int bi = nextPutIndex(1 + 2 + 2);
    _put(bi, a);
    unsafe.putShort(buffer, byteArrBaseOffset + bi + 1, b);
    // _put(bi + 1, short1(b));
    // _put(bi + 2, short0(b));
    unsafe.putShort(buffer, byteArrBaseOffset + bi + 3, c);
    // _put(bi + 3, short1(c));
    // _put(bi + 4, short0(c));
  }

  public void putByteShortInt(final byte a, final short b, final int c) {
    int bi = nextPutIndex(1 + 2 + 4);
    _put(bi, a);
    unsafe.putShort(buffer, byteArrBaseOffset + bi + 1, b);

    // _put(bi + 1, short1(b));
    // _put(bi + 2, short0(b));
    unsafe.putInt(buffer, byteArrBaseOffset + bi + 3, c);
    // _put(bi + 3, int3(c));
    // _put(bi + 4, int2(c));
    // _put(bi + 5, int1(c));
    // _put(bi + 6, int0(c));
  }

  public void putByteIntInt(final byte a, final int b, final int c) {
    int bi = nextPutIndex(1 + 4 + 4);
    _put(bi, a);
    unsafe.putInt(buffer, byteArrBaseOffset + bi + 1, b);
    // _put(bi + 1, int3(b));
    // _put(bi + 2, int2(b));
    // _put(bi + 3, int1(b));
    // _put(bi + 4, int0(b));
    unsafe.putInt(buffer, byteArrBaseOffset + bi + 5, c);
    // _put(bi + 5, int3(c));
    // _put(bi + 6, int2(c));
    // _put(bi + 7, int1(c));
    // _put(bi + 8, int0(c));
  }

  public void putShortInt(final short a, final int b) {
    int bi = nextPutIndex(2 + 4);
    unsafe.putShort(buffer, byteArrBaseOffset + bi, a);
    unsafe.putInt(buffer, byteArrBaseOffset + bi + 2, b);
  }

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
}
