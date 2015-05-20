package som.primitives.arrays;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SArray;
import som.vmobjects.SArray.ArrayType;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;


@GenerateNodeFactory
@ImportStatic(ArrayType.class)
public abstract class CopyPrim extends UnaryExpressionNode {

  @Specialization(guards = "isEmptyType(receiver)")
  public final SArray doEmptyArray(final SArray receiver) {
    return new SArray(receiver.getEnclosingObject(),
        receiver.getEmptyStorage());
  }

  @Specialization(guards = "isPartiallyEmptyType(receiver)")
  public final SArray doPartiallyEmptyArray(final SArray receiver) {
    return new SArray(receiver.getEnclosingObject(),
        ArrayType.PARTIAL_EMPTY, receiver.getPartiallyEmptyStorage().copy());
  }

  @Specialization(guards = "isObjectType(receiver)")
  public final SArray doObjectArray(final SArray receiver) {
    return SArray.create(receiver.getEnclosingObject(),
        receiver.getObjectStorage().clone());
  }

  @Specialization(guards = "isLongType(receiver)")
  public final SArray doLongArray(final SArray receiver) {
    return SArray.create(receiver.getEnclosingObject(),
        receiver.getLongStorage().clone());
  }

  @Specialization(guards = "isDoubleType(receiver)")
  public final SArray doDoubleArray(final SArray receiver) {
    return SArray.create(receiver.getEnclosingObject(),
        receiver.getDoubleStorage().clone());
  }

  @Specialization(guards = "isBooleanType(receiver)")
  public final SArray doBooleanArray(final SArray receiver) {
    return SArray.create(receiver.getEnclosingObject(),
        receiver.getBooleanStorage().clone());
  }
}
