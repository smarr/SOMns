package som.primitives.arrays;

import java.util.Arrays;

import som.compiler.Tags;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.primitives.Primitive;
import som.vm.constants.Nil;
import som.vmobjects.SArray.PartiallyEmptyArray;
import som.vmobjects.SArray.SMutableArray;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;


@GenerateNodeFactory
@ImportStatic(Nil.class)
@Primitive("array:at:put:")
public abstract class AtPutPrim extends TernaryExpressionNode {

  private final ValueProfile storageType = ValueProfile.createClassProfile();

  protected AtPutPrim(final SourceSection source) {
    super(SOMNode.cloneAndAddTags(source, Tags.ARRAY_WRITE));
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

  @Specialization(guards = {"receiver.isEmptyType()"})
  public final long doEmptySArray(final SMutableArray receiver, final long index,
      final long value) {
    long idx = index - 1;
    assert idx >= 0;
    assert idx < receiver.getEmptyStorage(storageType);

    receiver.transitionFromEmptyToPartiallyEmptyWith(idx, value);
    return value;
  }

  @Specialization(guards = {"receiver.isEmptyType()"})
  public final Object doEmptySArray(final SMutableArray receiver, final long index,
      final double value) {
    long idx = index - 1;
    assert idx >= 0;
    assert idx < receiver.getEmptyStorage(storageType);

    receiver.transitionFromEmptyToPartiallyEmptyWith(idx, value);
    return value;
  }

  @Specialization(guards = {"receiver.isEmptyType()"})
  public final Object doEmptySArray(final SMutableArray receiver, final long index,
      final boolean value) {
    long idx = index - 1;
    assert idx >= 0;
    assert idx < receiver.getEmptyStorage(storageType);

    receiver.transitionFromEmptyToPartiallyEmptyWith(idx, value);
    return value;
  }

  @Specialization(guards = {"receiver.isEmptyType()", "valueIsNotNil(value)",
      "valueNotLongDoubleBoolean(value)"})
  public final Object doEmptySArray(final SMutableArray receiver, final long index,
      final Object value) {
    final int idx = (int) index - 1;
    int size = receiver.getEmptyStorage(storageType);

    assert idx >= 0;
    assert idx < size;

    // if the value is an object, we transition directly to an Object array
    Object[] newStorage = new Object[size];
    Arrays.fill(newStorage, Nil.nilObject);
    newStorage[idx] = value;

    receiver.transitionTo(newStorage);
    return value;
  }

  @Specialization(guards = {"receiver.isEmptyType()", "valueIsNil(value)"})
  public final Object doEmptySArrayWithNil(final SMutableArray receiver, final long index,
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

  @Specialization(guards = "receiver.isPartiallyEmptyType()")
  public final long doPartiallyEmptySArray(final SMutableArray receiver,
      final long index, final long value) {
    long idx = index - 1;
    PartiallyEmptyArray storage = receiver.getPartiallyEmptyStorage(storageType);
    setValue(idx, value, storage);
    if (storage.getType() != PartiallyEmptyArray.Type.LONG) {
      storage.setType(PartiallyEmptyArray.Type.OBJECT);
    }

    receiver.ifFullOrObjectTransitionPartiallyEmpty();
    return value;
  }

  @Specialization(guards = "receiver.isPartiallyEmptyType()")
  public final double doPartiallyEmptySArray(final SMutableArray receiver,
      final long index, final double value) {
    long idx = index - 1;
    PartiallyEmptyArray storage = receiver.getPartiallyEmptyStorage(storageType);
    setValue(idx, value, storage);
    if (storage.getType() != PartiallyEmptyArray.Type.DOUBLE) {
      storage.setType(PartiallyEmptyArray.Type.OBJECT);
    }
    receiver.ifFullOrObjectTransitionPartiallyEmpty();
    return value;
  }

  @Specialization(guards = "receiver.isPartiallyEmptyType()")
  public final boolean doPartiallyEmptySArray(final SMutableArray receiver,
      final long index, final boolean value) {
    long idx = index - 1;
    PartiallyEmptyArray storage = receiver.getPartiallyEmptyStorage(storageType);
    setValue(idx, value, storage);
    if (storage.getType() != PartiallyEmptyArray.Type.BOOLEAN) {
      storage.setType(PartiallyEmptyArray.Type.OBJECT);
    }
    receiver.ifFullOrObjectTransitionPartiallyEmpty();
    return value;
  }

  @Specialization(guards = {"receiver.isPartiallyEmptyType()", "valueIsNil(value)"})
  public final Object doPartiallyEmptySArrayWithNil(final SMutableArray receiver,
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

  @Specialization(guards = {"receiver.isPartiallyEmptyType()", "valueIsNotNil(value)"})
  public final Object doPartiallyEmptySArray(final SMutableArray receiver,
      final long index, final Object value) {
    long idx = index - 1;
    PartiallyEmptyArray storage = receiver.getPartiallyEmptyStorage(storageType);
    setValue(idx, value, storage);
    storage.setType(PartiallyEmptyArray.Type.OBJECT);
    receiver.ifFullOrObjectTransitionPartiallyEmpty();
    return value;
  }

  @Specialization(guards = "receiver.isObjectType()")
  public final Object doObjectSArray(final SMutableArray receiver, final long index,
      final Object value) {
    long idx = index - 1;
    receiver.getObjectStorage(storageType)[(int) idx] = value;
    return value;
  }

  @Specialization(guards = "receiver.isLongType()")
  public final Object doObjectSArray(final SMutableArray receiver, final long index,
      final long value) {
    long idx = index - 1;
    receiver.getLongStorage(storageType)[(int) idx] = value;
    return value;
  }

  @Specialization(guards = {"receiver.isLongType()", "valueIsNotLong(value)"})
  public final Object doLongSArray(final SMutableArray receiver, final long index,
      final Object value) {
    long idx = index - 1;

    long[] storage = receiver.getLongStorage(storageType);
    Object[] newStorage = new Object[storage.length];
    for (int i = 0; i < storage.length; i++) {
      newStorage[i] = storage[i];
    }

    receiver.transitionTo(newStorage);
    newStorage[(int) idx] = value;
    return value;
  }

  @Specialization(guards = "receiver.isDoubleType()")
  public final Object doDoubleSArray(final SMutableArray receiver, final long index,
      final double value) {
    long idx = index - 1;
    receiver.getDoubleStorage(storageType)[(int) idx] = value;
    return value;
  }

  @Specialization(guards = {"receiver.isDoubleType()", "valueIsNotDouble(value)"})
  public final Object doDoubleSArray(final SMutableArray receiver, final long index,
      final Object value) {
    long idx = index - 1;

    double[] storage = receiver.getDoubleStorage(storageType);
    Object[] newStorage = new Object[storage.length];
    for (int i = 0; i < storage.length; i++) {
      newStorage[i] = storage[i];
    }

    receiver.transitionTo(newStorage);
    newStorage[(int) idx] = value;
    return value;
  }

  @Specialization(guards = "receiver.isBooleanType()")
  public final Object doBooleanSArray(final SMutableArray receiver, final long index,
      final boolean value) {
    long idx = index - 1;
    receiver.getBooleanStorage(storageType)[(int) idx] = value;
    return value;
  }

  @Specialization(guards = {"receiver.isBooleanType()", "valueIsNotBoolean(value)"})
  public final Object doBooleanSArray(final SMutableArray receiver, final long index,
      final Object value) {
    long idx = index - 1;

    boolean[] storage = receiver.getBooleanStorage(storageType);
    Object[] newStorage = new Object[storage.length];
    for (int i = 0; i < storage.length; i++) {
      newStorage[i] = storage[i];
    }

    receiver.transitionTo(newStorage);
    newStorage[(int) idx] = value;
    return value;
  }
}
