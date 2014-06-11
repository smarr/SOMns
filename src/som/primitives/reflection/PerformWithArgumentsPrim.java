package som.primitives.reflection;

import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class PerformWithArgumentsPrim extends TernaryExpressionNode {

  @Child protected AbstractSymbolDispatch dispatch;

  public PerformWithArgumentsPrim() {
    super(false);
    dispatch = AbstractSymbolDispatch.create();
  }

  @Specialization
  public final Object doObject(final VirtualFrame frame,
      final Object receiver, final SSymbol selector, final Object[]  argsArr) {
    return dispatch.executeDispatch(frame, receiver, selector, argsArr);
  }
}
