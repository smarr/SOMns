package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class NewObjectPrim extends UnaryExpressionNode {
  private final Universe universe;
  public NewObjectPrim() { this.universe = Universe.current(); }

  @Specialization
  public SAbstractObject doSClass(final SClass receiver) {
    return universe.newInstance(receiver);
  }
  @Override
  public final void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
}
