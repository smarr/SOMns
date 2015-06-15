package som.primitives.arrays;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SArray;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.utilities.ValueProfile;


public abstract class CopyPrim extends UnaryExpressionNode {

  private final ValueProfile storageType = ValueProfile.createClassProfile();

  @Specialization(guards = "receiver.isEmptyType()")
  public final SArray doEmptyArray(final SArray receiver) {
    return new SArray(receiver.getEmptyStorage(storageType));
  }

  @Specialization(guards = "receiver.isPartiallyEmptyType()")
  public final SArray doPartiallyEmptyArray(final SArray receiver) {
    return new SArray(true, receiver.getPartiallyEmptyStorage(storageType).copy());
  }

  @Specialization(guards = "receiver.isObjectType()")
  public final SArray doObjectArray(final SArray receiver) {
    return new SArray(receiver.getObjectStorage(storageType).clone());
  }

  @Specialization(guards = "receiver.isLongType()")
  public final SArray doLongArray(final SArray receiver) {
    return new SArray(receiver.getLongStorage(storageType).clone());
  }

  @Specialization(guards = "receiver.isDoubleType()")
  public final SArray doDoubleArray(final SArray receiver) {
    return new SArray(receiver.getDoubleStorage(storageType).clone());
  }

  @Specialization(guards = "receiver.isBooleanType()")
  public final SArray doBooleanArray(final SArray receiver) {
    return new SArray(receiver.getBooleanStorage(storageType).clone());
  }
}
