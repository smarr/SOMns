package som.primitives.reflection;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class PerformPrim extends BinaryExpressionNode {
  @Child protected AbstractSymbolDispatch dispatch;

  public PerformPrim(final boolean executesEnforced) { super(null, executesEnforced);
    dispatch = AbstractSymbolDispatch.create(false);
  }
  public PerformPrim(final PerformPrim node) { this(node.executesEnforced); }

  @Specialization
  public final Object doObject(final VirtualFrame frame, final Object receiver, final SSymbol selector) {
    return dispatch.executeDispatch(frame, receiver, selector, new Object[1]);
  }

  public abstract static class PerformEnforcedPrim extends PerformPrim {
    public PerformEnforcedPrim(final boolean executesEnforced) {
      super(executesEnforced);
      dispatch = AbstractSymbolDispatch.create(true);
    }
    public PerformEnforcedPrim(final PerformEnforcedPrim node) { this(node.executesEnforced); }
  }
}
