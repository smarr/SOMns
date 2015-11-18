package som.primitives.arrays;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.primitives.Primitive;
import som.vm.constants.Classes;
import som.vmobjects.SArray.SImmutableArray;
import som.vmobjects.SArray.SMutableArray;
import som.vmobjects.SArray.STransferArray;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


@GenerateNodeFactory
@Primitive("array:new:")
public abstract class NewPrim extends BinaryExpressionNode {

  protected static final boolean receiverIsArrayClass(final SClass receiver) {
    return receiver == Classes.arrayClass;
  }

  @Specialization(guards = {"receiverIsArrayClass(receiver)"})
  public final SMutableArray createArray(final SClass receiver, final long length) {
    return new SMutableArray(length);
  }

  protected static final boolean receiverIsValueArrayClass(final SClass receiver) {
    return receiver == Classes.valueArrayClass;
  }

  @Specialization(guards = {"receiverIsValueArrayClass(receiver)"})
  public final SImmutableArray createValueArray(final SClass receiver, final long length) {
    return new SImmutableArray(length);
  }

  protected static final boolean receiverIsTransferArrayClass(final SClass rcvr) {
    return rcvr == Classes.transferArrayClass;
  }

  @Specialization(guards = {"receiverIsTransferArrayClass(rcvr)"})
  protected static final STransferArray createTransferArray(final SClass rcvr, final long length) {
    return new STransferArray(length);
  }
}
