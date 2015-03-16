package som.primitives.arrays;

import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.ArrayType;
import som.vmobjects.SArray.PartiallyEmptyArray;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class AtPutPrim extends TernaryExpressionNode {

  public final static boolean isEmptyType(final SArray receiver) {
    return receiver.getType() == ArrayType.EMPTY;
  }

  public final static boolean isPartiallyEmptyType(final SArray receiver) {
    return receiver.getType() == ArrayType.PARTIAL_EMPTY;
  }

  public final static boolean isObjectType(final SArray receiver) {
    return receiver.getType() == ArrayType.OBJECT;
  }

  public final static boolean isLongType(final SArray receiver) {
    return receiver.getType() == ArrayType.LONG;
  }

  public final static boolean isDoubleType(final SArray receiver) {
    return receiver.getType() == ArrayType.DOUBLE;
  }

  public final static boolean isBooleanType(final SArray receiver) {
    return receiver.getType() == ArrayType.BOOLEAN;
  }

  protected final static boolean valueIsNil(final SArray rcvr, final long idx,
      final Object value) {
    return value == Nil.nilObject;
  }

  protected final static boolean valueIsNotNil(final SArray rcvr, final long idx,
      final Object value) {
    return value != Nil.nilObject;
  }

  protected final static boolean valueIsNotLong(final SArray rcvr, final long idx,
      final Object value) {
    return !(value instanceof Long);
  }

  protected final static boolean valueIsNotDouble(final SArray rcvr, final long idx,
      final Object value) {
    return !(value instanceof Double);
  }

  protected final static boolean valueIsNotBoolean(final SArray rcvr, final long idx,
      final Object value) {
    return !(value instanceof Boolean);
  }


  protected final static boolean valueNotLongDoubleBoolean(final SArray rcvr,
      final long idx, final Object value) {
    return !(value instanceof Long) &&
        !(value instanceof Double) &&
        !(value instanceof Boolean);
  }

  @Specialization(guards = {"isEmptyType"})
  public final long doEmptySArray(final SArray receiver, final long index,
      final long value) {
    long idx = index - 1;
    assert idx >= 0;
    assert idx < receiver.getEmptyStorage();

    receiver.transitionFromEmptyToPartiallyEmptyWith(idx, value);
    return value;
  }

  @Specialization(guards = {"isEmptyType"})
  public final Object doEmptySArray(final SArray receiver, final long index,
      final double value) {
    long idx = index - 1;
    assert idx >= 0;
    assert idx < receiver.getEmptyStorage();

    receiver.transitionFromEmptyToPartiallyEmptyWith(idx, value);
    return value;
  }

  @Specialization(guards = {"isEmptyType"})
  public final Object doEmptySArray(final SArray receiver, final long index,
      final boolean value) {
    long idx = index - 1;
    assert idx >= 0;
    assert idx < receiver.getEmptyStorage();

    receiver.transitionFromEmptyToPartiallyEmptyWith(idx, value);
    return value;
  }

  @Specialization(guards = {"isEmptyType", "valueIsNotNil", "valueNotLongDoubleBoolean"})
  public final Object doEmptySArray(final SArray receiver, final long index,
      final Object value) {
    long idx = index - 1;
    assert idx >= 0;
    assert idx < receiver.getEmptyStorage();

    receiver.transitionFromEmptyToPartiallyEmptyWith(idx, value);
    return value;
  }

  @Specialization(guards = {"isEmptyType", "valueIsNil"})
  public final Object doEmptySArrayWithNil(final SArray receiver, final long index,
      final Object value) {
    long idx = index - 1;
    assert idx >= 0;
    assert idx < receiver.getEmptyStorage();
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

  @Specialization(guards = "isPartiallyEmptyType")
  public final long doPartiallyEmptySArray(final SArray receiver,
      final long index, final long value) {
    long idx = index - 1;
    PartiallyEmptyArray storage = receiver.getPartiallyEmptyStorage();
    setValue(idx, value, storage);
    if (storage.getType() != ArrayType.LONG) {
      storage.setType(ArrayType.OBJECT);
    }

    receiver.ifFullTransitionPartiallyEmpty();
    return value;
  }

  @Specialization(guards = "isPartiallyEmptyType")
  public final double doPartiallyEmptySArray(final SArray receiver,
      final long index, final double value) {
    long idx = index - 1;
    PartiallyEmptyArray storage = receiver.getPartiallyEmptyStorage();
    setValue(idx, value, storage);
    if (storage.getType() != ArrayType.DOUBLE) {
      storage.setType(ArrayType.OBJECT);
    }
    receiver.ifFullTransitionPartiallyEmpty();
    return value;
  }

  @Specialization(guards = "isPartiallyEmptyType")
  public final boolean doPartiallyEmptySArray(final SArray receiver,
      final long index, final boolean value) {
    long idx = index - 1;
    PartiallyEmptyArray storage = receiver.getPartiallyEmptyStorage();
    setValue(idx, value, storage);
    if (storage.getType() != ArrayType.BOOLEAN) {
      storage.setType(ArrayType.OBJECT);
    }
    receiver.ifFullTransitionPartiallyEmpty();
    return value;
  }

  @Specialization(guards = {"isPartiallyEmptyType", "valueIsNil"})
  public final Object doPartiallyEmptySArrayWithNil(final SArray receiver,
      final long index, final Object value) {
    long idx = index - 1;
    PartiallyEmptyArray storage = receiver.getPartiallyEmptyStorage();
    assert idx >= 0;
    assert idx < storage.getLength();

    if (storage.get(idx) != Nil.nilObject) {
      storage.incEmptyElements();
      setValue(idx, Nil.nilObject, storage);
    }
    return value;
  }

  @Specialization(guards = {"isPartiallyEmptyType", "valueIsNotNil"})
  public final Object doPartiallyEmptySArray(final SArray receiver,
      final long index, final Object value) {
    long idx = index - 1;
    PartiallyEmptyArray storage = receiver.getPartiallyEmptyStorage();
    setValue(idx, value, storage);
    storage.setType(ArrayType.OBJECT);
    receiver.ifFullTransitionPartiallyEmpty();
    return value;
  }

  @Specialization(guards = "isObjectType")
  public final Object doObjectSArray(final SArray receiver, final long index,
      final Object value) {
    long idx = index - 1;
    receiver.getObjectStorage()[(int) idx] = value;
    return value;
  }

  @Specialization(guards = "isLongType")
  public final Object doObjectSArray(final SArray receiver, final long index,
      final long value) {
    long idx = index - 1;
    receiver.getLongStorage()[(int) idx] = value;
    return value;
  }

  @Specialization(guards = {"isLongType", "valueIsNotLong"})
  public final Object doLongSArray(final SArray receiver, final long index,
      final Object value) {
    long idx = index - 1;

    long[] storage = receiver.getLongStorage();
    Object[] newStorage = new Object[storage.length];
    for (int i = 0; i < storage.length; i++) {
      newStorage[i] = storage[i];
    }

    receiver.transitionTo(ArrayType.OBJECT, newStorage);
    newStorage[(int) idx] = value;
    return value;
  }

  @Specialization(guards = "isDoubleType")
  public final Object doDoubleSArray(final SArray receiver, final long index,
      final double value) {
    long idx = index - 1;
    receiver.getDoubleStorage()[(int) idx] = value;
    return value;
  }

  @Specialization(guards = {"isDoubleType", "valueIsNotDouble"})
  public final Object doDoubleSArray(final SArray receiver, final long index,
      final Object value) {
    long idx = index - 1;

    double[] storage = receiver.getDoubleStorage();
    Object[] newStorage = new Object[storage.length];
    for (int i = 0; i < storage.length; i++) {
      newStorage[i] = storage[i];
    }

    receiver.transitionTo(ArrayType.OBJECT, newStorage);
    newStorage[(int) idx] = value;
    return value;
  }

  @Specialization(guards = "isBooleanType")
  public final Object doBooleanSArray(final SArray receiver, final long index,
      final boolean value) {
    long idx = index - 1;
    receiver.getBooleanStorage()[(int) idx] = value;
    return value;
  }

  @Specialization(guards = {"isBooleanType", "valueIsNotBoolean"})
  public final Object doBooleanSArray(final SArray receiver, final long index,
      final Object value) {
    long idx = index - 1;

    boolean[] storage = receiver.getBooleanStorage();
    Object[] newStorage = new Object[storage.length];
    for (int i = 0; i < storage.length; i++) {
      newStorage[i] = storage[i];
    }

    receiver.transitionTo(ArrayType.OBJECT, newStorage);
    newStorage[(int) idx] = value;
    return value;
  }
}
