package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class NewObjectPrim extends UnarySideEffectFreeExpressionNode {
  @Specialization
  public final SAbstractObject doSClass(final SClass receiver) {
    return Universe.newInstance(receiver);
  }
}
