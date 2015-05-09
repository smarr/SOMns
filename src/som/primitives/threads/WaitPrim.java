package som.primitives.threads;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.constants.ThreadClasses;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


@GenerateNodeFactory
public abstract class WaitPrim extends UnaryExpressionNode {

  public WaitPrim() { super(null); }

  protected final boolean isDelayObject(final SObject receiver) {
    return receiver.getSOMClass() == ThreadClasses.delayClass;
  }

  @Specialization(guards = "isDelayObject(receiver)")
  public final SObject doWait(final SObject receiver) {
    try {
      Thread.sleep((long) receiver.getField(0));
    } catch (InterruptedException e) { /* Not relevant for the moment */ }
    return receiver;
  }

}
