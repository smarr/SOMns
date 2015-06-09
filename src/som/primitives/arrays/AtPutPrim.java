package som.primitives.arrays;

import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.primitives.Primitive;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.ArrayType;
import som.vmobjects.SArray.PartiallyEmptyArray;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.utilities.ValueProfile;


@GenerateNodeFactory
@ImportStatic(ArrayType.class)
@Primitive("array:at:put:")
public abstract class AtPutPrim extends TernaryExpressionNode {

  private final ValueProfile storageType = ValueProfile.createClassProfile();

  protected static final boolean valueIsNil(final Object value) {
    return value == Nil.nilObject;
  }

  protected static final boolean valueIsNotNil(final Object value) {
    return value != Nil.nilObject;
  }

  protected static final boolean valueIsNotLong(final Object value) {
    return !(value instanceof Long);
  }

  protected static final boolean valueIsNotDouble(final Object value) {
    return !(value instanceof Double);
  }

  protected static final boolean valueIsNotBoolean(final Object value) {
    return !(value instanceof Boolean);
  }

  protected static final boolean valueNotLongDoubleBoolean(final Object value) {
    return !(value instanceof Long) &&
        !(value instanceof Double) &&
        !(value instanceof Boolean);
  }

  @Specialization(guards = {"isEmptyType(receiver)"})
  public final long doEmptySArray(final SArray receiver, final long index,
      final long value) {
    long idx = index - 1;
    assert idx >= 0;
    assert idx < receiver.getEmptyStorage(storageType);

    receiver.transitionFromEmptyToPartiallyEmptyWith(idx, value);
    return value;
  }

  @Specialization(guards = {"isEmptyType(receiver)"})
  public final Object doEmptySArray(final SArray receiver, final long index,
      final double value) {
    long idx = index - 1;
    assert idx >= 0;
    assert idx < receiver.getEmptyStorage(storageType);

    receiver.transitionFromEmptyToPartiallyEmptyWith(idx, value);
    return value;
  }

  @Specialization(guards = {"isEmptyType(receiver)"})
  public final Object doEmptySArray(final SArray receiver, final long index,
      final boolean value) {
    long idx = index - 1;
    assert idx >= 0;
    assert idx < receiver.getEmptyStorage(storageType);

    receiver.transitionFromEmptyToPartiallyEmptyWith(idx, value);
    return value;
  }

  @Specialization(guards = {"isEmptyType(receiver)", "valueIsNotNil(value)", "valueNotLongDoubleBoolean(value)"})
  public final Object doEmptySArray(final SArray receiver, final long index,
      final Object value) {
    long idx = index - 1;
    assert idx >= 0;
    assert idx < receiver.getEmptyStorage(storageType);

    receiver.transitionFromEmptyToPartiallyEmptyWith(idx, value);
    return value;
  }

  @Specialization(guards = {"isEmptyType(receiver)", "valueIsNil(value)"})
  public final Object doEmptySArrayWithNil(final SArray receiver, final long index,
      final Object value) {
    long idx = index - 1;
    assert idx >= 0;
    assert idx < receiver.getEmptyStorage(storageType);
    return value;
  }

  private void setValue(final long idx, final Object value,
      final PartiallyEmptyArray storage) {
    assert idx >= 0;
    assert idx < storage.getLength();

    if (storage.get(idx) == Nil.nilObject) {
      storage.decEmptyElements();
    }
    storage.set(idx, value);
  }

  @Specialization(guards = "isPartiallyEmptyType(receiver)")
  public final long doPartiallyEmptySArray(final SArray receiver,
      final long index, final long value) {
    long idx = index - 1;
    PartiallyEmptyArray storage = receiver.getPartiallyEmptyStorage(storageType);
    setValue(idx, value, storage);
    if (storage.getType() != ArrayType.LONG) {
      storage.setType(ArrayType.OBJECT);
    }

    receiver.ifFullTransitionPartiallyEmpty();
    return value;
  }

  @Specialization(guards = "isPartiallyEmptyType(receiver)")
  public final double doPartiallyEmptySArray(final SArray receiver,
      final long index, final double value) {
    long idx = index - 1;
    PartiallyEmptyArray storage = receiver.getPartiallyEmptyStorage(storageType);
    setValue(idx, value, storage);
    if (storage.getType() != ArrayType.DOUBLE) {
      storage.setType(ArrayType.OBJECT);
    }
    receiver.ifFullTransitionPartiallyEmpty();
    return value;
  }

  @Specialization(guards = "isPartiallyEmptyType(receiver)")
  public final boolean doPartiallyEmptySArray(final SArray receiver,
      final long index, final boolean value) {
    long idx = index - 1;
    PartiallyEmptyArray storage = receiver.getPartiallyEmptyStorage(storageType);
    setValue(idx, value, storage);
    if (storage.getType() != ArrayType.BOOLEAN) {
      storage.setType(ArrayType.OBJECT);
    }
    receiver.ifFullTransitionPartiallyEmpty();
    return value;
  }

  @Specialization(guards = {"isPartiallyEmptyType(receiver)", "valueIsNil(value)"})
  public final Object doPartiallyEmptySArrayWithNil(final SArray receiver,
      final long index, final Object value) {
    long idx = index - 1;
    PartiallyEmptyArray storage = receiver.getPartiallyEmptyStorage(storageType);
    assert idx >= 0;
    assert idx < storage.getLength();

    if (storage.get(idx) != Nil.nilObject) {
      storage.incEmptyElements();
      setValue(idx, Nil.nilObject, storage);
    }
    return value;
  }

  @Specialization(guards = {"isPartiallyEmptyType(receiver)", "valueIsNotNil(value)"})
  public final Object doPartiallyEmptySArray(final SArray receiver,
      final long index, final Object value) {
    long idx = index - 1;
    PartiallyEmptyArray storage = receiver.getPartiallyEmptyStorage(storageType);
    setValue(idx, value, storage);
    storage.setType(ArrayType.OBJECT);
    receiver.ifFullTransitionPartiallyEmpty();
    return value;
  }

  @Specialization(guards = "isObjectType(receiver)")
  public final Object doObjectSArray(final SArray receiver, final long index,
      final Object value) {
    long idx = index - 1;
    receiver.getObjectStorage(storageType)[(int) idx] = value;
    return value;
  }

  @Specialization(guards = "isLongType(receiver)")
  public final Object doObjectSArray(final SArray receiver, final long index,
      final long value) {
    long idx = index - 1;
    receiver.getLongStorage(storageType)[(int) idx] = value;
    return value;
  }

  @Specialization(guards = {"isLongType(receiver)", "valueIsNotLong(value)"})
  public final Object doLongSArray(final SArray receiver, final long index,
      final Object value) {
    long idx = index - 1;

    long[] storage = receiver.getLongStorage(storageType);
    Object[] newStorage = new Object[storage.length];
    for (int i = 0; i < storage.length; i++) {
      newStorage[i] = storage[i];
    }

    receiver.transitionTo(ArrayType.OBJECT, newStorage);
    newStorage[(int) idx] = value;
    return value;
  }

  @Specialization(guards = "isDoubleType(receiver)")
  public final Object doDoubleSArray(final SArray receiver, final long index,
      final double value) {
    long idx = index - 1;
    receiver.getDoubleStorage(storageType)[(int) idx] = value;
    return value;
  }

  @Specialization(guards = {"isDoubleType(receiver)", "valueIsNotDouble(value)"})
  public final Object doDoubleSArray(final SArray receiver, final long index,
      final Object value) {
    long idx = index - 1;

    double[] storage = receiver.getDoubleStorage(storageType);
    Object[] newStorage = new Object[storage.length];
    for (int i = 0; i < storage.length; i++) {
      newStorage[i] = storage[i];
    }

    receiver.transitionTo(ArrayType.OBJECT, newStorage);
    newStorage[(int) idx] = value;
    return value;
  }

  @Specialization(guards = "isBooleanType(receiver)")
  public final Object doBooleanSArray(final SArray receiver, final long index,
      final boolean value) {
    long idx = index - 1;
    receiver.getBooleanStorage(storageType)[(int) idx] = value;
    return value;
  }

  @Specialization(guards = {"isBooleanType(receiver)", "valueIsNotBoolean(value)"})
  public final Object doBooleanSArray(final SArray receiver, final long index,
      final Object value) {
    long idx = index - 1;

    boolean[] storage = receiver.getBooleanStorage(storageType);
    Object[] newStorage = new Object[storage.length];
    for (int i = 0; i < storage.length; i++) {
      newStorage[i] = storage[i];
    }

    receiver.transitionTo(ArrayType.OBJECT, newStorage);
    newStorage[(int) idx] = value;
    return value;
  }
}
