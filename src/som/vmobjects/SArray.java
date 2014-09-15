package som.vmobjects;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;


public final class SArray {
  public  static final int FIRST_IDX = 0;

  public static Object[] newSArray(final Object[] nonSArray) {
    return nonSArray; // This is a no-op, but in the OMOP branch it isn't
  }

  public static Object[] newSArray(final long length, final SObject nilObject) {
    Object[] result = new Object[(int) length];
    Arrays.fill(result, nilObject);
    return result;
  }

  public static Object get(final Object[] arr, final long idx) {
    assert idx >= 0;
    assert idx < arr.length;
    return CompilerDirectives.unsafeCast(arr[(int) idx], Object.class, true, true);
  }

  public static void set(final Object[] arr, final long idx, final Object value) {
    assert idx >= 0;
    assert idx < arr.length;
    assert value != null;

    arr[(int) idx] = value;
  }

  public static long length(final Object[] arr) {
    return arr.length;
  }
}
