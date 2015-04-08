package som.primitives.arrays;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vm.constants.Classes;
import som.vmobjects.SArray;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class NewPrim extends BinaryExpressionNode {

  protected static final boolean receiverIsArrayClass(final SClass receiver) {
    return receiver == Classes.arrayClass;
  }

  @Specialization(guards = "receiverIsArrayClass(receiver)")
  public final SArray doSClass(final SClass receiver, final long length) {
    return new SArray(length);
  }
}
