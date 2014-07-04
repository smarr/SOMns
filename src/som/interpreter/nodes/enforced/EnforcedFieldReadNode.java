package som.interpreter.nodes.enforced;

import som.interpreter.SArguments;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.FieldNode.AbstractFieldReadNode;
import som.vm.Universe;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;


public final class EnforcedFieldReadNode extends AbstractFieldReadNode {
  // chain based on domain
  // holder == domainClass => standard domain shortcut
  // otherwise, cached dispatch to intercession handler like DNU

  private final long fieldIndex; /// ???
  private final SSymbol intercessionHandler;

  public EnforcedFieldReadNode(final ExpressionNode self, final long fieldIndex,
      final SourceSection source) {
    super(self, source, true);
    this.fieldIndex = fieldIndex + 1;
    intercessionHandler = Universe.current().symbolFor("readField:of:");
  }

  @Override
  public Object executeEvaluated(final VirtualFrame frame, final SObject obj) {
    CompilerAsserts.neverPartOfCompilation("EnforcedFieldReadNode");
    SObject rcvrDomain    = obj.getDomain();
    SObject currentDomain = SArguments.domain(frame);
    SInvokable handler = rcvrDomain.getSOMClass(null).lookupInvokable(intercessionHandler);
    return handler.invoke(currentDomain, false, rcvrDomain, fieldIndex, obj);
  }

  @Override
  public void executeVoid(final VirtualFrame frame) {
    executeGeneric(frame);
  }
}
