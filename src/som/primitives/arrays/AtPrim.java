package som.primitives.arrays;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.primitives.Primitive;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.ArrayType;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.utilities.ValueProfile;


@GenerateNodeFactory
@ImportStatic(ArrayType.class)
@Primitive("array:at:")
public abstract class AtPrim extends BinaryExpressionNode {

  private final ValueProfile storageType = ValueProfile.createClassProfile();

  @Specialization(guards = "isEmptyType(receiver)")
  public final Object doEmptySArray(final SArray receiver, final long idx) {
    assert idx > 0;
    assert idx <= receiver.getEmptyStorage(storageType);
    return Nil.nilObject;
  }

  @Specialization(guards = "isPartiallyEmptyType(receiver)")
  public final Object doPartiallyEmptySArray(final SArray receiver, final long idx) {
    return receiver.getPartiallyEmptyStorage(storageType).get(idx - 1);
  }

  @Specialization(guards = "isObjectType(receiver)")
  public final Object doObjectSArray(final SArray receiver, final long idx) {
    return receiver.getObjectStorage(storageType)[(int) idx - 1];
  }

  @Specialization(guards = "isLongType(receiver)")
  public final long doLongSArray(final SArray receiver, final long idx) {
    return receiver.getLongStorage(storageType)[(int) idx - 1];
  }

  @Specialization(guards = "isDoubleType(receiver)")
  public final double doDoubleSArray(final SArray receiver, final long idx) {
    return receiver.getDoubleStorage(storageType)[(int) idx - 1];
  }

  @Specialization(guards = "isBooleanType(receiver)")
  public final boolean doBooleanSArray(final SArray receiver, final long idx) {
    return receiver.getBooleanStorage(storageType)[(int) idx - 1];
  }
}
