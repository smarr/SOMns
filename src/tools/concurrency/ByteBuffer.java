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

  public ByteBuffer putShort(final short x) {
    int bi = nextPutIndex(2);
    unsafe.putShort(buffer, byteArrBaseOffset + bi, x);

    return this;
  }

  public ByteBuffer putShortAt(final int idx, final short x) {
    unsafe.putShort(buffer, byteArrBaseOffset + idx, x);
    return this;
  }

  public ByteBuffer putLong(final long x) {
    int bi = nextPutIndex(8);
    unsafe.putLong(buffer, byteArrBaseOffset + bi, x);

    return this;
  }

  public ByteBuffer putDouble(final double x) {
    putLong(Double.doubleToRawLongBits(x));
    return this;
  }

  public ByteBuffer putInt(final int x) {
    int bi = nextPutIndex(4);
    unsafe.putInt(buffer, byteArrBaseOffset + bi, x);

    return this;
  }

  public ByteBuffer putIntAt(final int idx, final int x) {
    unsafe.putInt(buffer, byteArrBaseOffset + idx, x);
    return this;
  }

  public ByteBuffer put(final byte x) {
    unsafe.putByte(buffer, byteArrBaseOffset + nextPutIndex(), x);
    return this;
  }

  public ByteBuffer putByteAt(final int idx, final byte x) {
    unsafe.putByte(buffer, byteArrBaseOffset + idx, x);
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

  public void putByteByteShortShortInt(final byte a, final byte b, final short c,
      final short d, final int e) {
    int bi = nextPutIndex(1 + 1 + 2 + 2 + 4);
    _put(bi, a);
    _put(bi + 1, b);
    unsafe.putShort(buffer, byteArrBaseOffset + bi + 2, b);
    unsafe.putShort(buffer, byteArrBaseOffset + bi + 4, b);
    unsafe.putInt(buffer, byteArrBaseOffset + bi + 6, e);
  }

  public void putByteByteShortInt(final byte a, final byte b, final short c, final int d) {
    int bi = nextPutIndex(1 + 1 + 2 + 4);
    _put(bi, a);
    _put(bi + 1, b);
    unsafe.putShort(buffer, byteArrBaseOffset + bi + 2, b);
    unsafe.putInt(buffer, byteArrBaseOffset + bi + 4, b);
  }

  public void putByteShort(final byte a, final short b) {
    int bi = nextPutIndex(1 + 2);
    _put(bi, a);
    unsafe.putShort(buffer, byteArrBaseOffset + bi + 1, b);
    // _put(bi + 1, short1(b));
    // _put(bi + 2, short0(b));
  }

  public void putByteShortAt(final int idx, final byte a, final short b) {
    unsafe.putByte(buffer, byteArrBaseOffset + idx, a);
    unsafe.putShort(buffer, byteArrBaseOffset + idx + 1, b);
  }

  public void putByteShortByte(final byte a, final short b, final byte c) {
    int bi = nextPutIndex(1 + 2 + 1);
    _put(bi, a);
    unsafe.putShort(buffer, byteArrBaseOffset + bi + 1, b);
    _put(bi + 3, c);
  }

  public void putByteShortByteShort(final byte a, final short b, final byte c, final short d) {
    int bi = nextPutIndex(1 + 2 + 1 + 2);
    _put(bi, a);
    unsafe.putShort(buffer, byteArrBaseOffset + bi + 1, b);
    _put(bi + 3, c);
    unsafe.putShort(buffer, byteArrBaseOffset + bi + 4, d);
  }

  public void putByteShortShortInt(final byte a, final short b, final short c, final int d) {
    int bi = nextPutIndex(1 + 2 + 2 + 4);
    _put(bi, a);
    unsafe.putShort(buffer, byteArrBaseOffset + bi + 1, b);
    unsafe.putShort(buffer, byteArrBaseOffset + bi + 3, c);
    unsafe.putInt(buffer, byteArrBaseOffset + bi + 5, d);
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

  public void putByteInt(final byte a, final int b) {
    int bi = nextPutIndex(1 + 4);
    _put(bi, a);
    unsafe.putInt(buffer, byteArrBaseOffset + bi + 1, b);
  }

  public void putByteIntShortInt(final byte a, final int b, final short c, final int d) {
    int bi = nextPutIndex(1 + 4 + 2 + 4);
    _put(bi, a);

    unsafe.putInt(buffer, byteArrBaseOffset + bi + 1, b);
    unsafe.putShort(buffer, byteArrBaseOffset + bi + 5, c);
    unsafe.putInt(buffer, byteArrBaseOffset + bi + 7, d);
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
