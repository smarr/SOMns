package som.vmobjects;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;


public final class SArray {
  private static final int OWNER_IDX = 0;
  public  static final int FIRST_IDX = OWNER_IDX + 1;

  public static Object[] newSArray(final Object[] nonSArray, final SObject domain) {
    assert domain != null;

    Object[] arr = new Object[nonSArray.length + 1];
    System.arraycopy(nonSArray, 0, arr, 1, nonSArray.length);
    arr[OWNER_IDX] = domain;
    return arr;
  }

  public static Object[] newSArray(final long length, final SObject nilObject, final SObject domain) {
    assert domain != null;

    Object[] result = new Object[(int) length + 1];
    Arrays.fill(result, nilObject);
    result[OWNER_IDX] = domain;
    return result;
  }

  public static Object get(final Object[] arr, final long idx) {
    assert idx > OWNER_IDX;
    assert idx <= arr.length;

    return CompilerDirectives.unsafeCast(arr[(int) idx], Object.class,
        true, true);
  }

  public static void set(final Object[] arr, final long idx, final Object value) {
    assert idx > OWNER_IDX;
    assert idx <= arr.length;
    assert value != null;

    arr[(int) idx] = value;
  }

  public static long length(final Object[] arr) {
    return arr.length - 1;
  }

  public static Object[] fromSArrayToArgArrayWithReceiver(final Object[] somArray, final Object receiver) {
    assert receiver != null;

    Object[] argArray = Arrays.copyOf(somArray, somArray.length);
    argArray[OWNER_IDX] = receiver;
    return argArray;
  }

  public static Object[] fromArgArrayWithReceiverToSArrayWithoutReceiver(final Object[] argArray, final SObject domain) {
    assert domain != null;

    Object[] somArray = Arrays.copyOf(argArray, argArray.length);
    argArray[OWNER_IDX] = domain;
    return somArray;
  }

  public static SObject getOwner(final Object[] arr) {
    assert arr[OWNER_IDX] != null;
    return CompilerDirectives.unsafeCast(arr[OWNER_IDX], SObject.class,
        true, true);
  }

  public static void setOwner(final Object[] arr, final SObject domain) {
    assert domain != null;
    arr[OWNER_IDX] = domain;
  }
}
