package som.vmobjects;

import java.util.Arrays;

import som.vm.constants.Classes;
import som.vm.constants.Nil;

/**
 * SArrays are implemented using a Strategy-like approach.
 * The SArray objects are 'tagged' with a type, and the strategy behavior
 * is implemented directly in the AST nodes.
 *
 * @author smarr
 */
public final class SArray extends SAbstractObject {
  public static final int FIRST_IDX = 0;

  public static SArray create(final Object[] values) {
    return new SArray(values);
  }

  public static SArray create(final int length) {
    return new SArray(length);
  }

  private ArrayType type;
  private Object    storage;

  public ArrayType getType() {
    return type;
  }

  public int getEmptyStorage() {
    assert type == ArrayType.EMPTY;
    return (int) storage;
  }

  public PartiallyEmptyArray getPartiallyEmptyStorage() {
    assert type == ArrayType.PARTIAL_EMPTY;
    return (PartiallyEmptyArray) storage;
  }

  public Object[] getObjectStorage() {
    assert type == ArrayType.OBJECT;
    return (Object[]) storage;
  }

  /**
   * Creates and empty array, using the EMPTY strategy.
   * @param length
   */
  public SArray(final long length) {
    type = ArrayType.EMPTY;
    storage = (int) length;
  }

  private SArray(final Object[] val) {
    type = ArrayType.OBJECT;
    storage = val;
  }

  public SArray(final ArrayType type, final Object storage) {
    this.type    = type;
    this.storage = storage;
  }

  /**
   * Transition from the Empty, to the PartiallyEmpty state/strategy
   */
  public void transitionFromEmptyToPartiallyEmptyWith(final long idx, final Object val) {
    assert type == ArrayType.EMPTY;

    int length = (int) storage;
    storage = new PartiallyEmptyArray(length, idx, val);
    type    = ArrayType.PARTIAL_EMPTY;
  }

  public void transitionToEmpty(final long length) {
    type = ArrayType.EMPTY;
    storage = (int) length;
  }

  public void transitionToObject(final Object[] newStorage) {
    type = ArrayType.OBJECT;
    storage = newStorage;
  }

  public void transitionToObjectWithAll(final long length, final Object val) {
    type = ArrayType.OBJECT;
    Object[] arr = new Object[(int) length];
    Arrays.fill(arr, val);
    storage = arr;
  }

  public enum ArrayType { EMPTY, PARTIAL_EMPTY, LONG, DOUBLE,
    /* TODO:? BOOLEAN,*/  OBJECT }

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

  public void setPartiallyEmpty(final long idx, final Object val) {
    // TODO: CompilerAsserts.neverPartOfCompilation();

    PartiallyEmptyArray arr = getPartiallyEmptyStorage();

    if (val == Nil.nilObject) {
      if (arr.get(idx) != Nil.nilObject) {
        arr.set(idx, val);
        arr.incEmptyElements();
      }
      return;
    }

    if (arr.get(idx) == Nil.nilObject) {
      arr.decEmptyElements();
    }

    arr.set(idx, val);

    // now, based on the type of the element, we might need to adjust the
    // type the full array will have in the end
    if (val instanceof Long) {
      if (arr.getType() == ArrayType.EMPTY) {
        arr.setType(ArrayType.LONG);
      } else if (arr.getType() == ArrayType.DOUBLE) {
        arr.setType(ArrayType.OBJECT);
      }
    } else if (val instanceof Double) {
      if (arr.getType() == ArrayType.EMPTY) {
        arr.setType(ArrayType.DOUBLE);
      } else if (arr.getType() == ArrayType.LONG) {
        arr.setType(ArrayType.OBJECT);
      }
    } else {
      arr.setType(ArrayType.OBJECT);
    }

    if (arr.isFull()) {
      if (arr.getType() == ArrayType.LONG) {
        type = ArrayType.LONG;
        storage = createLong(arr.getStorage());
      } else if (arr.getType() == ArrayType.DOUBLE) {
        type = ArrayType.DOUBLE;
        storage = createDouble(arr.getStorage());
      } else {
        type = ArrayType.OBJECT;
        storage = arr.getStorage();
      }
    }
  }

  public static final class PartiallyEmptyArray {
    private final Object[] arr;
    private int emptyElements;
    private ArrayType type;

    public PartiallyEmptyArray(final int length, final long idx, final Object val) {
      arr = new Object[length];
      Arrays.fill(arr, Nil.nilObject);
      emptyElements = length - 1;
      arr[(int) idx] = val;
    }

    private PartiallyEmptyArray(final PartiallyEmptyArray old) {
      arr = old.arr.clone();
      emptyElements = old.emptyElements;
      type = old.type;
    }

    public ArrayType getType() {
      return type;
    }

    public Object[] getStorage() {
      return arr;
    }

    public void setType(final ArrayType type) {
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

  /**
   * For internal use only, specifically, for SClass.
   * There we now, it is either empty, or of OBJECT type.
   * @param value
   * @return
   */
  public SArray copyAndExtendWith(final Object value) {
    Object[] newArr;
    if (type == ArrayType.EMPTY) {
      newArr = new Object[] { value };
    } else {
      // if this is not true, this method is used in a wrong context
      assert type == ArrayType.OBJECT;
      Object[] s = getObjectStorage();
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
