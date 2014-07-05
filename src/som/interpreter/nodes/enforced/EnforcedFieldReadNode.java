package som.interpreter.nodes.enforced;

import som.interpreter.SArguments;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.FieldNode.AbstractFieldReadNode;
import som.interpreter.nodes.dispatch.DispatchChain.Cost;
import som.interpreter.nodes.enforced.IntercessionHandlerCache.AbstractIntercessionHandlerDispatch;
import som.vmobjects.SObject;
import src.com.oracle.truffle.api.SourceSection;
import src.com.oracle.truffle.api.frame.VirtualFrame;
import src.com.oracle.truffle.api.nodes.Node.Child;
import src.com.oracle.truffle.api.nodes.NodeCost;


public final class EnforcedFieldReadNode extends AbstractFieldReadNode {
  // chain based on domain
  // holder == domainClass => standard domain shortcut
  // otherwise, cached dispatch to intercession handler like DNU

  private final long fieldIndex;
  private final long somFieldIndex;

  @Child private AbstractIntercessionHandlerDispatch dispatch;

  public EnforcedFieldReadNode(final ExpressionNode self, final long fieldIndex,
      final SourceSection source) {
    super(self, source, true);
    this.fieldIndex    = fieldIndex;
    this.somFieldIndex = fieldIndex + 1;
    dispatch = IntercessionHandlerCache.create("readField:of:", executesEnforced);
  }

  @Override
  public Object executeEvaluated(final VirtualFrame frame, final SObject obj) {
    SObject rcvrDomain    = obj.getDomain();
    SObject currentDomain = SArguments.domain(frame);

    Object[] arguments = SArguments.createSArgumentsArray(false, currentDomain,
        rcvrDomain, somFieldIndex, obj);

    return dispatch.executeDispatch(frame, rcvrDomain, arguments);
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
