package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class HashPrim extends UnaryExpressionNode {
  @Specialization
  public int doSString(final String receiver) {
    return receiver.hashCode();
  }

  @Specialization
  public int doSAbstractObject(final SAbstractObject receiver) {
    return receiver.hashCode();
  }

  @Override
  public final void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
}
