package tools.concurrency;

import java.nio.BufferOverflowException;


public final class ByteBuffer {
  public static ByteBuffer allocate(final int capacity) {
    return new ByteBuffer(capacity);
  }

  private int position;
  private int limit;
  private int capacity;

  private final byte[] buffer;

  private ByteBuffer(final int capacity) {
    buffer = new byte[capacity];
    this.capacity = capacity;
    this.limit = capacity;
    this.position = 0;
  }

  public java.nio.ByteBuffer getBuffer() {
    return java.nio.ByteBuffer.wrap(buffer, 0, limit);
  }

  public int position() {
    return position;
  }

  public ByteBuffer position(final int newPosition) {
    if ((newPosition > limit) || (newPosition < 0)) {
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
    return limit - position;
  }

  public ByteBuffer clear() {
    position = 0;
    limit = capacity;
    return this;
  }

  public ByteBuffer limit(final int newLimit) {
    if ((newLimit > capacity) || (newLimit < 0)) {
      throw new IllegalArgumentException();
    }
    limit = newLimit;
    if (position > limit) {
      position = limit;
    }
    return this;
  }

  private int nextPutIndex() {
    if (position >= limit) {
      throw new BufferOverflowException();
    }
    return position++;
  }

  private int nextPutIndex(final int nb) {
    if (limit - position < nb) {
      throw new BufferOverflowException();
    }
    int p = position;
    position += nb;
    return p;
  }

  private void _put(final int i, final byte b) {
    buffer[i] = b;
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
    buffer[nextPutIndex()] = x;
    return this;
  }

  public ByteBuffer put(final byte[] src) {
    return put(src, 0, src.length);
  }

  private static void checkBounds(final int off, final int len, final int size) {
    if ((off | len | (off + len) | (size - (off + len))) < 0) {
      throw new IndexOutOfBoundsException();
    }
  }

  public ByteBuffer put(final byte[] src, final int offset, final int length) {

    checkBounds(offset, length, src.length);
    if (length > remaining()) {
      throw new BufferOverflowException();
    }
    System.arraycopy(src, offset, buffer, position(), length);
    position(position() + length);
    return this;

  }
}
