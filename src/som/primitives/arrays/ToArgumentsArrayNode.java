package som.primitives.arrays;

import java.util.Arrays;

import som.interpreter.SArguments;
import som.interpreter.nodes.ExpressionNode;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.ArrayType;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChildren({
  @NodeChild("somArray"),
  @NodeChild("receiver")})
public abstract class ToArgumentsArrayNode extends ExpressionNode {

  public ToArgumentsArrayNode() { super(null); }

  public final static boolean isNull(final Object somArray) {
    return somArray == null;
  }

  public final static boolean isEmptyType(final SArray somArray) {
    return somArray.getType() == ArrayType.EMPTY;
  }

  public final static boolean isPartiallyEmptyType(final SArray somArray) {
    return somArray.getType() == ArrayType.PARTIAL_EMPTY;
  }

  public final static boolean isObjectType(final SArray somArray) {
    return somArray.getType() == ArrayType.OBJECT;
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

  public abstract Object[] executedEvaluated(SArray somArray, Object rcvr);

  public final Object[] executedEvaluated(final Object somArray, final Object rcvr) {
    return executedEvaluated((SArray) somArray, rcvr);
  }

  @Specialization(guards = "isNull")
  public final Object[] doNoArray(final Object somArray, final Object rcvr) {
    return new Object[] {rcvr};
  }

  @Specialization(guards = "isEmptyType")
  public final Object[] doEmptyArray(final SArray somArray, final Object rcvr) {
    Object[] result = new Object[somArray.getEmptyStorage() + 1];
    Arrays.fill(result, Nil.nilObject);
    result[SArguments.RCVR_IDX] = rcvr;
    return result;
  }

  private Object[] addRcvrToObjectArray(final Object rcvr, final Object[] storage) {
    Object[] argsArray = new Object[storage.length + 1];
    argsArray[SArguments.RCVR_IDX] = rcvr;
    System.arraycopy(storage, 0, argsArray, 1, storage.length);
    return argsArray;
  }

  @Specialization(guards = "isPartiallyEmptyType")
  public final Object[] doPartiallyEmptyArray(final SArray somArray,
      final Object rcvr) {
    return addRcvrToObjectArray(
        rcvr, somArray.getPartiallyEmptyStorage().getStorage());
  }

  @Specialization(guards = "isObjectType")
  public final Object[] doObjectArray(final SArray somArray,
      final Object rcvr) {
    return addRcvrToObjectArray(rcvr, somArray.getObjectStorage());
  }

  @Specialization(guards = "isLongType")
  public final Object[] doLongArray(final SArray somArray,
      final Object rcvr) {
    long[] arr = somArray.getLongStorage();
    Object[] args = new Object[arr.length + 1];
    args[0] = rcvr;
    for (int i = 0; i < arr.length; i++) {
      args[i + 1] = arr[i];
    }
    return args;
  }

  @Specialization(guards = "isDoubleType")
  public final Object[] doDoubleArray(final SArray somArray,
      final Object rcvr) {
    double[] arr = somArray.getDoubleStorage();
    Object[] args = new Object[arr.length + 1];
    args[0] = rcvr;
    for (int i = 0; i < arr.length; i++) {
      args[i + 1] = arr[i];
    }
    return args;
  }

  @Specialization(guards = "isBooleanType")
  public final Object[] doBooleanArray(final SArray somArray,
      final Object rcvr) {
    boolean[] arr = somArray.getBooleanStorage();
    Object[] args = new Object[arr.length + 1];
    args[0] = rcvr;
    for (int i = 0; i < arr.length; i++) {
      args[i + 1] = arr[i];
    }
    return args;
  }
}
