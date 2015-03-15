package som.primitives.arrays;

import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.ArrayType;

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

  @Specialization(guards = "isEmptyType")
  public final Object doEmptySArray(final SArray receiver, final long idx,
      final Object value) {
    assert idx > 0;
    assert idx <= receiver.getEmptyStorage();

    if (value == Nil.nilObject) {
      // everything is nil already, avoids transition...
      return Nil.nilObject;
    }

    receiver.transitionFromEmptyToPartiallyEmptyWith(idx - 1, value);
    return value;
  }

  @Specialization(guards = "isPartiallyEmptyType")
  public final Object doPartiallyEmptySArray(final SArray receiver,
      final long idx, final Object value) {
    assert idx > 0;
    assert idx <= receiver.getPartiallyEmptyStorage().getLength();

    receiver.setPartiallyEmpty(idx - 1, value);
    return value;
  }

  @Specialization(guards = "isObjectType")
  public final Object doObjectSArray(final SArray receiver, final long idx,
      final Object value) {
    receiver.getObjectStorage()[(int) idx - 1] = value;
    return value;
  }

  // TODO: we actually need to add support to transition to Object
  // strategy if something other than a long/double comes along
  @Specialization(guards = "isLongType")
  public final Object doObjectSArray(final SArray receiver, final long idx,
      final long value) {
    receiver.getLongStorage()[(int) idx - 1] = value;
    return value;
  }

  @Specialization(guards = "isDoubleType")
  public final Object doDoubleSArray(final SArray receiver, final long idx,
      final double value) {
    receiver.getDoubleStorage()[(int) idx - 1] = value;
    return value;
  }
}
