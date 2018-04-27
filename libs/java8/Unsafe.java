package sun.misc;

import java.lang.reflect.Field;


// Hack to compile on Java 9 with `--release 8`
public final class Unsafe {
  public static Unsafe getUnsafe() {
    return null;
  }

  public native long objectFieldOffset(Field f);

  public native Object getObject(Object o, long offset);

  public native void putObject(Object o, long offset, Object value);

  public native void putByte(Object o, long offset, byte value);

  public native void putShort(Object o, long offset, short value);
  
  public native void putInt(Object o, long offset, int value);
  
  public native long getLong(Object o, long offset);

  public native void putLong(Object o, long offset, long value);

  public native double getDouble(Object o, long offset);

  public native void putDouble(Object o, long offset, double value);
}
