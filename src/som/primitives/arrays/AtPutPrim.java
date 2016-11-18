package som.primitives.arrays;

import java.util.Arrays;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.primitives.Primitive;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.PartiallyEmptyArray;
import som.vmobjects.SArray.SMutableArray;
import tools.dym.Tags.ArrayWrite;
import tools.dym.Tags.BasicPrimitiveOperation;


@GenerateNodeFactory
@ImportStatic(Nil.class)
@Primitive(primitive = "array:at:put:", selector = "at:put:",
           receiverType = SArray.class)
public abstract class AtPutPrim extends TernaryExpressionNode {
  private final ValueProfile storageType = ValueProfile.createClassProfile();

  protected AtPutPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == BasicPrimitiveOperation.class) {
      return true;
    } else if (tag == ArrayWrite.class) {
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
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
    receiver.transitionFromEmptyToPartiallyEmptyWith(index - 1, value);
    return value;
  }

  @Specialization(guards = {"receiver.isEmptyType()"})
  public final Object doEmptySArray(final SMutableArray receiver, final long index,
      final double value) {
    receiver.transitionFromEmptyToPartiallyEmptyWith(index - 1, value);
    return value;
  }

  @Specialization(guards = {"receiver.isEmptyType()"})
  public final Object doEmptySArray(final SMutableArray receiver, final long index,
      final boolean value) {
    receiver.transitionFromEmptyToPartiallyEmptyWith(index - 1, value);
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

  private static void setValue(final long idx, final Object value,
      final PartiallyEmptyArray storage) {
    if (storage.get(idx) == Nil.nilObject) {
      storage.decEmptyElements();
    }
    storage.set(idx, value);
  }

  private void setAndPossiblyTransition(final SMutableArray receiver,
      final long index, final Object value, final PartiallyEmptyArray.Type expectedType) {
    PartiallyEmptyArray storage = receiver.getPartiallyEmptyStorage(storageType);
    setValue(index - 1, value, storage);
    if (storage.getType() != expectedType) {
      storage.setType(PartiallyEmptyArray.Type.OBJECT);
    }
    receiver.ifFullOrObjectTransitionPartiallyEmpty();
  }

  @Specialization(guards = "receiver.isPartiallyEmptyType()")
  public final long doPartiallyEmptySArray(final SMutableArray receiver,
      final long index, final long value) {
    setAndPossiblyTransition(receiver, index, value, PartiallyEmptyArray.Type.LONG);
    return value;
  }

  @Specialization(guards = "receiver.isPartiallyEmptyType()")
  public final double doPartiallyEmptySArray(final SMutableArray receiver,
      final long index, final double value) {
    setAndPossiblyTransition(receiver, index, value, PartiallyEmptyArray.Type.DOUBLE);
    return value;
  }

  @Specialization(guards = "receiver.isPartiallyEmptyType()")
  public final boolean doPartiallyEmptySArray(final SMutableArray receiver,
      final long index, final boolean value) {
    setAndPossiblyTransition(receiver, index, value, PartiallyEmptyArray.Type.BOOLEAN);
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
      storage.set(idx, Nil.nilObject);
    }
    return value;
  }

  @Specialization(guards = {"receiver.isPartiallyEmptyType()", "valueIsNotNil(value)"})
  public final Object doPartiallyEmptySArray(final SMutableArray receiver,
      final long index, final Object value) {
    setAndPossiblyTransition(receiver, index, value, PartiallyEmptyArray.Type.OBJECT);
    return value;
  }

  @Specialization(guards = "receiver.isObjectType()")
  public final Object doObjectSArray(final SMutableArray receiver, final long index,
      final Object value) {
    receiver.getObjectStorage(storageType)[(int) index - 1] = value;
    return value;
  }

  @Specialization(guards = "receiver.isLongType()")
  public final Object doObjectSArray(final SMutableArray receiver, final long index,
      final long value) {
    receiver.getLongStorage(storageType)[(int) index - 1] = value;
    return value;
  }

  @Specialization(guards = {"receiver.isLongType()", "valueIsNotLong(value)"})
  public final Object doLongSArray(final SMutableArray receiver, final long index,
      final Object value) {
    long[] storage = receiver.getLongStorage(storageType);
    Object[] newStorage = new Object[storage.length];
    for (int i = 0; i < storage.length; i++) {
      newStorage[i] = storage[i];
    }

    return transitionAndSet(receiver, index, value, newStorage);
  }

  private Object transitionAndSet(final SMutableArray receiver,
      final long index, final Object value, final Object[] newStorage) {
    receiver.transitionTo(newStorage);
    newStorage[(int) index - 1] = value;
    return value;
  }

  @Specialization(guards = "receiver.isDoubleType()")
  public final Object doDoubleSArray(final SMutableArray receiver, final long index,
      final double value) {
    receiver.getDoubleStorage(storageType)[(int) index - 1] = value;
    return value;
  }

  @Specialization(guards = {"receiver.isDoubleType()", "valueIsNotDouble(value)"})
  public final Object doDoubleSArray(final SMutableArray receiver, final long index,
      final Object value) {
    double[] storage = receiver.getDoubleStorage(storageType);
    Object[] newStorage = new Object[storage.length];
    for (int i = 0; i < storage.length; i++) {
      newStorage[i] = storage[i];
    }
    return transitionAndSet(receiver, index, value, newStorage);
  }

  @Specialization(guards = "receiver.isBooleanType()")
  public final Object doBooleanSArray(final SMutableArray receiver, final long index,
      final boolean value) {
    receiver.getBooleanStorage(storageType)[(int) index - 1] = value;
    return value;
  }

  @Specialization(guards = {"receiver.isBooleanType()", "valueIsNotBoolean(value)"})
  public final Object doBooleanSArray(final SMutableArray receiver, final long index,
      final Object value) {
    boolean[] storage = receiver.getBooleanStorage(storageType);
    Object[] newStorage = new Object[storage.length];
    for (int i = 0; i < storage.length; i++) {
      newStorage[i] = storage[i];
    }
    return transitionAndSet(receiver, index, value, newStorage);
  }
}
