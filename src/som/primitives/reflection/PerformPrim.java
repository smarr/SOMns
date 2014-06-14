package som.primitives.reflection;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class PerformPrim extends BinaryExpressionNode {
  @Child protected AbstractSymbolDispatch dispatch;

  public PerformPrim() { super(null, false); /* TODO: enforced!!! */
    dispatch = AbstractSymbolDispatch.create(false);
  }

  @Specialization
  public final Object doObject(final VirtualFrame frame, final Object receiver, final SSymbol selector) {
    return dispatch.executeDispatch(frame, receiver, selector, new Object[0]);
  }

  public abstract static class PerformEnforcedPrim extends PerformPrim {
    public PerformEnforcedPrim() {
      dispatch = AbstractSymbolDispatch.create(true);
    }
  }
}
