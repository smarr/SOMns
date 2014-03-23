package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class NewObjectPrim extends UnarySideEffectFreeExpressionNode {
  private final Universe universe;
  public NewObjectPrim() { this.universe = Universe.current(); }

  @Specialization
  public final SAbstractObject doSClass(final SClass receiver) {
    return universe.newInstance(receiver);
  }
}
