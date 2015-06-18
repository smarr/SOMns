package som.vmobjects;

import java.util.Arrays;

import som.vm.constants.Classes;
import som.vm.constants.Nil;

import com.oracle.truffle.api.utilities.ValueProfile;

/**
 * SArrays are implemented using a Strategy-like approach.
 * The SArray objects are 'tagged' with a type, and the strategy behavior
 * is implemented directly in the AST nodes.
 *
 * @author smarr
 */
public final class SArray extends SAbstractObject {
  public static final int FIRST_IDX = 0;

  private Object storage;

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

  /**
   * Creates and empty array, using the EMPTY strategy.
   * @param length
   */
  public SArray(final long length) {
    storage = (int) length;
  }

  public SArray(final Object[] val) {
    storage = val;
  }

  public SArray(final long[] val) {
    storage = val;
  }

  public SArray(final double[] val) {
    storage = val;
  }

  public SArray(final boolean[] val) {
    storage = val;
  }

  public SArray(final boolean withStorage, final Object storage) {
    assert withStorage;
    this.storage = storage;
  }

  private void fromEmptyToParticalWithType(final PartiallyEmptyArray.Type type,
      final long idx, final Object val) {
    assert type != PartiallyEmptyArray.Type.OBJECT;
    assert isEmptyType();
    storage = new PartiallyEmptyArray(type, (int) storage, idx, val);
  }

  /**
   * Transition from the Empty, to the PartiallyEmpty state/strategy.
   * We don't transition to Partial with Object, because, there is no more
   * specialization that could be applied.
   */
  public void transitionFromEmptyToPartiallyEmptyWith(final long idx, final long val) {
    fromEmptyToParticalWithType(PartiallyEmptyArray.Type.LONG, idx, val);
  }

  public void transitionFromEmptyToPartiallyEmptyWith(final long idx, final double val) {
    fromEmptyToParticalWithType(PartiallyEmptyArray.Type.DOUBLE, idx, val);
  }

  public void transitionFromEmptyToPartiallyEmptyWith(final long idx, final boolean val) {
    fromEmptyToParticalWithType(PartiallyEmptyArray.Type.BOOLEAN, idx, val);
  }

  public void transitionToEmpty(final long length) {
    storage = (int) length;
  }

  public void transitionTo(final Object newStorage) {
    storage = newStorage;
  }

//  private static final ValueProfile emptyStorageType = ValueProfile.createClassProfile();

  public void transitionToObjectWithAll(final long length, final Object val) {
    Object[] arr = new Object[(int) length];
    Arrays.fill(arr, val);
    storage = arr;
  }

  public void transitionToLongWithAll(final long length, final long val) {
    long[] arr = new long[(int) length];
    Arrays.fill(arr, val);
    storage = arr;
  }

  public void transitionToDoubleWithAll(final long length, final double val) {
    double[] arr = new double[(int) length];
    Arrays.fill(arr, val);
    storage = arr;
  }

  public void transitionToBooleanWithAll(final long length, final boolean val) {
    boolean[] arr = new boolean[(int) length];
    if (val) {
      Arrays.fill(arr, true);
    }
    storage = arr;
  }

  public boolean isEmptyType() {
    return storage instanceof Integer;
  }

  public boolean isPartiallyEmptyType() {
    return storage instanceof PartiallyEmptyArray;
  }

  public boolean isObjectType() {
    return storage instanceof Object[];
  }

  public boolean isLongType() {
    return storage instanceof long[];
  }

  public boolean isDoubleType() {
    return storage instanceof double[];
  }

  public boolean isBooleanType() {
    return storage instanceof boolean[];
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

  private static final ValueProfile partiallyEmptyStorageType = ValueProfile.createClassProfile();

  public void ifFullOrObjectTransitionPartiallyEmpty() {
    PartiallyEmptyArray arr = getPartiallyEmptyStorage(partiallyEmptyStorageType);

    if (arr.isFull()) {
      if (arr.getType() == PartiallyEmptyArray.Type.LONG) {
        storage = createLong(arr.getStorage());
        return;
      } else if (arr.getType() == PartiallyEmptyArray.Type.DOUBLE) {
        storage = createDouble(arr.getStorage());
        return;
      } else if (arr.getType() == PartiallyEmptyArray.Type.BOOLEAN) {
        storage = createBoolean(arr.getStorage());
        return;
      }
    }
    if (arr.getType() == PartiallyEmptyArray.Type.OBJECT) {
      storage = arr.getStorage();
    }
  }

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

  private static final ValueProfile objectStorageType = ValueProfile.createClassProfile();

  /**
   * For internal use only, specifically, for SClass.
   * There we now, it is either empty, or of OBJECT type.
   * @param value
   * @return
   */
  public SArray copyAndExtendWith(final Object value) {
    Object[] newArr;
    if (isEmptyType()) {
      newArr = new Object[] {value};
    } else {
      // if this is not true, this method is used in a wrong context
      assert isObjectType();
      Object[] s = getObjectStorage(objectStorageType);
      newArr = Arrays.copyOf(s, s.length + 1);
      newArr[s.length] = value;
    }
    return new SArray(newArr);
  }

  @Override
  public SClass getSOMClass() {
    return Classes.arrayClass;
  }
}
