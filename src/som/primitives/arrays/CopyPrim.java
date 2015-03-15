package som.primitives.arrays;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SArray;
import som.vmobjects.SArray.ArrayType;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class CopyPrim extends UnaryExpressionNode {
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

  @Specialization(guards = "isEmptyType")
  public final SArray doEmptyArray(final SArray receiver) {
    return new SArray(receiver.getEmptyStorage());
  }

  @Specialization(guards = "isPartiallyEmptyType")
  public final SArray doPartiallyEmptyArray(final SArray receiver) {
    return new SArray(ArrayType.PARTIAL_EMPTY, receiver.getPartiallyEmptyStorage().copy());
  }

  @Specialization(guards = "isObjectType")
  public final SArray doObjectArray(final SArray receiver) {
    return SArray.create(receiver.getObjectStorage().clone());
  }

  @Specialization(guards = "isLongType")
  public final SArray doLongArray(final SArray receiver) {
    return SArray.create(receiver.getLongStorage().clone());
  }

  @Specialization(guards = "isDoubleType")
  public final SArray doDoubleArray(final SArray receiver) {
    return SArray.create(receiver.getDoubleStorage().clone());
  }

  @Specialization(guards = "isBooleanType")
  public final SArray doBooleanArray(final SArray receiver) {
    return SArray.create(receiver.getBooleanStorage().clone());
  }
}
