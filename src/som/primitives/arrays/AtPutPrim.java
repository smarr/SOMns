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

  protected final static boolean valueIsNil(final SArray rcvr, final long idx,
      final Object value) {
    return value == Nil.nilObject;
  }

  protected final static boolean valueIsNotNil(final SArray rcvr, final long idx,
      final Object value) {
    return value != Nil.nilObject;
  }

  protected final static boolean valueNeitherLongNorDouble(final SArray rcvr,
      final long idx, final Object value) {
    return !(value instanceof Long) && !(value instanceof Double);
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

  @Specialization(guards = {"isEmptyType", "valueIsNotNil", "valueNeitherLongNorDouble"})
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
    if (storage.getType() == ArrayType.DOUBLE) {
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
    if (storage.getType() == ArrayType.LONG) {
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

  // TODO: we actually need to add support to transition to Object
  // strategy if something other than a long/double comes along
  @Specialization(guards = "isLongType")
  public final Object doObjectSArray(final SArray receiver, final long index,
      final long value) {
    long idx = index - 1;
    receiver.getLongStorage()[(int) idx] = value;
    return value;
  }

  @Specialization(guards = "isDoubleType")
  public final Object doDoubleSArray(final SArray receiver, final long index,
      final double value) {
    long idx = index - 1;
    receiver.getDoubleStorage()[(int) idx] = value;
    return value;
  }
}
