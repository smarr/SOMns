package som.interpreter.nodes.enforced;

import som.interpreter.SArguments;
import som.interpreter.nodes.FieldNode.AbstractFieldWriteNode;
import som.interpreter.nodes.dispatch.DispatchChain.Cost;
import som.interpreter.nodes.enforced.IntercessionHandlerCache.AbstractIntercessionHandlerDispatch;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;


public abstract class EnforcedFieldWriteNode extends AbstractFieldWriteNode {
  private final long fieldIndex;
  private final long somFieldIndex;

  @Child private AbstractIntercessionHandlerDispatch dispatch;

  public EnforcedFieldWriteNode(final long fieldIndex, final SourceSection source) {
    super(source, true);
    this.fieldIndex    = fieldIndex;
    this.somFieldIndex = fieldIndex + 1;
    dispatch = IntercessionHandlerCache.create("write:toField:of:", executesEnforced);
  }

  public EnforcedFieldWriteNode(final EnforcedFieldWriteNode node) {
    this(node.fieldIndex, node.getSourceSection());
  }

  @Specialization
  public final Object doSObject(final VirtualFrame frame, final SObject obj,
      final Object value) {
    SObject rcvrDomain = obj.getDomain();
    SObject currentDomain = SArguments.domain(frame);

    Object[] arguments = SArguments.createSArgumentsArray(false, currentDomain,
        rcvrDomain, value, somFieldIndex, obj);
    return dispatch.executeDispatch(frame, rcvrDomain, arguments);
  }

  @Override
  public final void executeVoid(final VirtualFrame frame) {
    executeGeneric(frame);
  }

  @Override
  public final NodeCost getCost() {
    return Cost.getCost(dispatch);
  }
}
