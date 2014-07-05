package som.primitives.reflection;

import som.interpreter.nodes.dispatch.DispatchChain.Cost;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;

public abstract class PerformWithArgumentsPrim extends TernaryExpressionNode {

  @Child protected AbstractSymbolDispatch dispatch;

  public PerformWithArgumentsPrim(final boolean executesEnforced) {
    super(executesEnforced);
    dispatch = AbstractSymbolDispatch.create(false);
  }
  public PerformWithArgumentsPrim(final PerformWithArgumentsPrim node) {
    this(node.executesEnforced);
  }

  @Specialization
  public final Object doObject(final VirtualFrame frame,
      final Object receiver, final SSymbol selector, final Object[]  argsArr) {
    return dispatch.executeDispatch(frame, receiver, selector, argsArr);
  }

  public abstract static class PerformEnforcedWithArgumentsPrim extends PerformWithArgumentsPrim {
    public PerformEnforcedWithArgumentsPrim(final boolean executesEnforced) {
      super(executesEnforced);
      dispatch = AbstractSymbolDispatch.create(true);
    }

    public PerformEnforcedWithArgumentsPrim(final PerformEnforcedWithArgumentsPrim node) {
      this(node.executesEnforced);
    }
  }

  @Override
  public final NodeCost getCost() {
    return Cost.getCost(dispatch);
  }
}
