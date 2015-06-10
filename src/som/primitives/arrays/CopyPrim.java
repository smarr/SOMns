package som.primitives.arrays;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SArray;
import som.vmobjects.SArray.ArrayType;

import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.utilities.ValueProfile;


@ImportStatic(ArrayType.class)
public abstract class CopyPrim extends UnaryExpressionNode {

  private final ValueProfile storageType = ValueProfile.createClassProfile();

  @Specialization(guards = "isEmptyType(receiver)")
  public final SArray doEmptyArray(final SArray receiver) {
    return new SArray(receiver.getEmptyStorage(storageType));
  }

  @Specialization(guards = "isPartiallyEmptyType(receiver)")
  public final SArray doPartiallyEmptyArray(final SArray receiver) {
    return new SArray(ArrayType.PARTIAL_EMPTY,
        receiver.getPartiallyEmptyStorage(storageType).copy());
  }

  @Specialization(guards = "isObjectType(receiver)")
  public final SArray doObjectArray(final SArray receiver) {
    return SArray.create(receiver.getObjectStorage(storageType).clone());
  }

  @Specialization(guards = "isLongType(receiver)")
  public final SArray doLongArray(final SArray receiver) {
    return SArray.create(receiver.getLongStorage(storageType).clone());
  }

  @Specialization(guards = "isDoubleType(receiver)")
  public final SArray doDoubleArray(final SArray receiver) {
    return SArray.create(receiver.getDoubleStorage(storageType).clone());
  }

  @Specialization(guards = "isBooleanType(receiver)")
  public final SArray doBooleanArray(final SArray receiver) {
    return SArray.create(receiver.getBooleanStorage(storageType).clone());
  }
}
