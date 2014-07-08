package som.interpreter.nodes.enforced;

import som.interpreter.SArguments;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.DispatchChain.Cost;
import som.interpreter.nodes.enforced.IntercessionHandlerCache.AbstractIntercessionHandlerDispatch;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;


public final class EnforcedGlobalReadNode extends ExpressionNode {

  private final SSymbol globalName;
  @Child private AbstractIntercessionHandlerDispatch dispatch;

  public EnforcedGlobalReadNode(final SSymbol globalName, final SourceSection source) {
    super(source, true);
    this.globalName = globalName;
    dispatch = IntercessionHandlerCache.create("readGlobal:for:", executesEnforced);
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    SObject currentDomain = SArguments.domain(frame);
    Object rcvr = SArguments.rcvr(frame);

    // reading globals is based on the current execution context, not the
    // receiver. thus, the difference with all other intercession handlers is
    // on purpose.

    Object[] arguments = SArguments.createSArgumentsArray(false, currentDomain,
        currentDomain, globalName, rcvr);

    return dispatch.executeDispatch(frame, currentDomain, arguments);
  }

  @Override
  public void executeVoid(final VirtualFrame frame) {
    executeGeneric(frame);
  }

  @Override
  public NodeCost getCost() {
    return Cost.getCost(dispatch);
  }
}
