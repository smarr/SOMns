package som.vmobjects;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.profiles.ValueProfile;

import som.vm.NotYetImplementedException;
import som.vm.constants.Nil;

/**
 * SArrays are implemented using a Strategy-like approach.
 * The SArray objects are 'tagged' with a type, and the strategy behavior
 * is implemented directly in the AST nodes.
 */
public abstract class SArray extends SAbstractObject {
  public static final int FIRST_IDX = 0;

  protected Object storage;
  protected final SClass clazz;

  public SArray(final long length, final SClass clazz) {
    storage = (int) length;
    this.clazz = clazz;
  }

  public SArray(final Object storage, final SClass clazz) {
    assert !(storage instanceof Long);
    assert storage != null;
    this.storage = storage;
    this.clazz   = clazz;
  }

  @Override
  public final SClass getSOMClass() {
    return clazz;
  }

  public Object getStoragePlain() {
    CompilerAsserts.neverPartOfCompilation();
    return storage;
  }

  public int getEmptyStorage(final ValueProfile storageType) {
    assert isEmptyType();
    return (int) storageType.profile(storage);
  }

  public PartiallyEmptyArray getPartiallyEmptyStorage(final ValueProfile storageType) {
    assert isPartiallyEmptyType();
    return (PartiallyEmptyArray) storageType.profile(storage);
  }

  public Object[] getObjectStorage(final ValueProfile storageType) {
    assert isObjectType();
    return (Object[]) storageType.profile(storage);
  }

  public long[] getLongStorage(final ValueProfile storageType) {
    assert isLongType();
    return (long[]) storageType.profile(storage);
  }

  public double[] getDoubleStorage(final ValueProfile storageType) {
    assert isDoubleType();
    return (double[]) storageType.profile(storage);
  }

  public boolean[] getBooleanStorage(final ValueProfile storageType) {
    assert isBooleanType();
    return (boolean[]) storageType.profile(storage);
  }

  public boolean isEmptyType() {
    return storage instanceof Integer;
  }

  public boolean isPartiallyEmptyType() {
    return storage instanceof PartiallyEmptyArray;
  }

  public boolean isObjectType()  { return storage.getClass() == Object[].class; }
  public boolean isLongType()    { return storage.getClass() == long[].class;   }
  public boolean isDoubleType()  { return storage.getClass() == double[].class; }
  public boolean isBooleanType() { return storage.getClass() == boolean[].class; }

  public boolean isSomePrimitiveType() {
    return isLongType() || isDoubleType() || isBooleanType();
  }


  private static long[] createLong(final Object[] arr) {
    long[] storage = new long[arr.length];
    for (int i = 0; i < arr.length; i++) {
      storage[i] = (long) arr[i];
    }
    return storage;
  }

  private static double[] createDouble(final Object[] arr) {
    double[] storage = new double[arr.length];
    for (int i = 0; i < arr.length; i++) {
      storage[i] = (double) arr[i];
    }
    return storage;
  }

  private static boolean[] createBoolean(final Object[] arr) {
    boolean[] storage = new boolean[arr.length];
    for (int i = 0; i < arr.length; i++) {
      storage[i] = (boolean) arr[i];
    }
    return storage;
  }

  public static final ValueProfile PartiallyEmptyStorageType = ValueProfile.createClassProfile();


  public static final class PartiallyEmptyArray {
    private final Object[] arr;
    private int emptyElements;
    private Type type;

    public enum Type {
      EMPTY, PARTIAL_EMPTY, LONG, DOUBLE, BOOLEAN, OBJECT;
    }

    public PartiallyEmptyArray(final Type type, final int length,
        final long idx, final Object val) {
      // can't specialize this here already,
      // because keeping track for nils would be to expensive
      arr = new Object[length];
      Arrays.fill(arr, Nil.nilObject);
      emptyElements = length - 1;
      arr[(int) idx] = val;
      this.type = type;
    }

    private PartiallyEmptyArray(final PartiallyEmptyArray old) {
      arr = old.arr.clone();
      emptyElements = old.emptyElements;
      type = old.type;
    }

    public Type getType() {
      return type;
    }

    public Object[] getStorage() {
      return arr;
    }

    public void setType(final Type type) {
      this.type = type;
    }

    public int getLength() {
      return arr.length;
    }

    public Object get(final long idx) {
      return arr[(int) idx];
    }

    public void set(final long idx, final Object val) {
      arr[(int) idx] = val;
    }

    public void incEmptyElements() { emptyElements++; }
    public void decEmptyElements() { emptyElements--; }
    public boolean isFull() { return emptyElements == 0; }

    public PartiallyEmptyArray copy() {
      return new PartiallyEmptyArray(this);
    }
  }

  public static final ValueProfile ObjectStorageType = ValueProfile.createClassProfile();

  public static class SMutableArray extends SArray {

    /**
     * Creates and empty array, using the EMPTY strategy.
     * @param length
     */
    public SMutableArray(final long length, final SClass clazz) {
      super(length, clazz);
    }

    public SMutableArray(final Object storage, final SClass clazz) {
      super(storage, clazz);
    }

    public SMutableArray shallowCopy() {
      Object storageClone;
      if (isEmptyType()) {
        storageClone = storage;
      } else if (isPartiallyEmptyType()) {
        storageClone = ((PartiallyEmptyArray) storage).copy();
      } else if (isBooleanType()) {
        storageClone = ((boolean[]) storage).clone();
      } else if (isDoubleType()) {
        storageClone = ((double[]) storage).clone();
      } else if (isLongType()) {
        storageClone = ((long[]) storage).clone();
      } else {
        assert isObjectType();
        storageClone = ((Object[]) storage).clone();
      }

      return new SMutableArray(storageClone, clazz);
    }

    public boolean txEquals(final SMutableArray a) {
      if (storage.getClass() != a.storage.getClass()) {
        // TODO: strictly speaking this isn't correct,
        //       there could be long[].equals(Object[]) that is true
        //       but, we are prone to ABA problems, so, I don't think this matters either
        return false;
      }

      if (isEmptyType()) {
        return true;
      } else if (isPartiallyEmptyType()) {
        return Arrays.equals(((PartiallyEmptyArray) storage).arr,  ((PartiallyEmptyArray) a.storage).arr);
      } else if (isBooleanType()) {
        return Arrays.equals((boolean[]) storage, (boolean[]) a.storage);
      } else if (isDoubleType()) {
        return Arrays.equals((double[]) storage,  (double[]) a.storage);
      } else if (isLongType()) {
        return Arrays.equals((long[]) storage,    (long[]) a.storage);
      } else {
        assert isObjectType();
        return Arrays.equals((Object[]) storage,  (Object[]) a.storage);
      }
    }

    public void txSet(final SMutableArray a) {
      storage = a.storage;
    }

    /**
     * For internal use only, specifically, for SClass.
     * There we now, it is either empty, or of OBJECT type.
     * @param value
     * @return new mutable array extended by value
     */
    public SArray copyAndExtendWith(final Object value) {
      Object[] newArr;
      if (isEmptyType()) {
        newArr = new Object[] {value};
      } else {
        // if this is not true, this method is used in a wrong context
        assert isObjectType();
        Object[] s = getObjectStorage(ObjectStorageType);
        newArr = Arrays.copyOf(s, s.length + 1);
        newArr[s.length] = value;
      }
      return new SMutableArray(newArr, clazz);
    }

    @Override
    public final boolean isValue() {
      return false;
    }

    private void fromEmptyToParticalWithType(final PartiallyEmptyArray.Type type,
        final long idx, final Object val) {
      assert type != PartiallyEmptyArray.Type.OBJECT;
      assert isEmptyType();
      this.storage = new PartiallyEmptyArray(type, (int) storage, idx, val);
    }

    /**
     * Transition from the Empty, to the PartiallyEmpty state/strategy.
     * We don't transition to Partial with Object, because, there is no more
     * specialization that could be applied.
     */
    public final void transitionFromEmptyToPartiallyEmptyWith(final long idx, final long val) {
      fromEmptyToParticalWithType(PartiallyEmptyArray.Type.LONG, idx, val);
    }

    public final void transitionFromEmptyToPartiallyEmptyWith(final long idx, final double val) {
      fromEmptyToParticalWithType(PartiallyEmptyArray.Type.DOUBLE, idx, val);
    }

    public final void transitionFromEmptyToPartiallyEmptyWith(final long idx, final boolean val) {
      fromEmptyToParticalWithType(PartiallyEmptyArray.Type.BOOLEAN, idx, val);
    }

    public final void transitionToEmpty(final long length) {
      this.storage = (int) length;
    }

    public final void transitionTo(final Object newStorage) {
      this.storage = newStorage;
    }

//    private static final ValueProfile emptyStorageType = ValueProfile.createClassProfile();

    public final void transitionToObjectWithAll(final long length, final Object val) {
      Object[] arr = new Object[(int) length];
      Arrays.fill(arr, val);
      final Object storage = arr;
      this.storage = storage;
    }

    public final void transitionToLongWithAll(final long length, final long val) {
      long[] arr = new long[(int) length];
      Arrays.fill(arr, val);
      final Object storage = arr;
      this.storage = storage;
    }

    public final void transitionToDoubleWithAll(final long length, final double val) {
      double[] arr = new double[(int) length];
      Arrays.fill(arr, val);
      final Object storage = arr;
      this.storage = storage;
    }

    public final void transitionToBooleanWithAll(final long length, final boolean val) {
      boolean[] arr = new boolean[(int) length];
      if (val) {
        Arrays.fill(arr, true);
      }
      final Object storage = arr;
      this.storage = storage;
    }

    public final void ifFullOrObjectTransitionPartiallyEmpty() {
      PartiallyEmptyArray arr = getPartiallyEmptyStorage(PartiallyEmptyStorageType);

      if (arr.isFull()) {
        if (arr.getType() == PartiallyEmptyArray.Type.LONG) {
          this.storage = createLong(arr.getStorage());
          return;
        } else if (arr.getType() == PartiallyEmptyArray.Type.DOUBLE) {
          this.storage = createDouble(arr.getStorage());
          return;
        } else if (arr.getType() == PartiallyEmptyArray.Type.BOOLEAN) {
          this.storage = createBoolean(arr.getStorage());
          return;
        }
      }
      if (arr.getType() == PartiallyEmptyArray.Type.OBJECT) {
        this.storage = arr.getStorage();
      }
    }
  }

  public static final class SImmutableArray extends SArray {

    public SImmutableArray(final long length, final SClass clazz) { super(length, clazz); }
    public SImmutableArray(final Object storage, final SClass clazz) { super(storage, clazz); }

    @Override
    public boolean isValue() { return true; }
  }

  public static final class STransferArray extends SMutableArray {
    public STransferArray(final long length, final SClass clazz) { super(length, clazz); }
    public STransferArray(final Object storage, final SClass clazz) { super(storage, clazz); }
    public STransferArray(final STransferArray old, final SClass clazz) { super(cloneStorage(old), clazz); }

    private static Object cloneStorage(final STransferArray old) {
      if (old.isEmptyType()) {
        return old.storage;
      } else if (old.isBooleanType()) {
        return ((boolean[]) old.storage).clone();
      } else if (old.isDoubleType()) {
        return ((double[]) old.storage).clone();
      } else if (old.isLongType()) {
        return ((long[]) old.storage).clone();
      } else if (old.isObjectType()) {
        return ((Object[]) old.storage).clone();
      } else if (old.isPartiallyEmptyType()) {
        return ((PartiallyEmptyArray) old.storage).copy();
      } else {
        CompilerDirectives.transferToInterpreter();
        assert false : "Support for some storage type missing?";
        throw new NotYetImplementedException();
      }
    }

    public STransferArray cloneBasics() {
      return new STransferArray(this, clazz);
    }
  }
}
