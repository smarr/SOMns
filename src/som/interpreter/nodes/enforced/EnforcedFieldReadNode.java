package som.interpreter.nodes.enforced;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.FieldNode.AbstractFieldReadNode;
import som.vm.Universe;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.VirtualFrame;


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
  public Object executeEvaluated(final SObject obj) {
    CompilerAsserts.neverPartOfCompilation();
    SObject domain = obj.getDomain();
    SInvokable handler = domain.getSOMClass(null).lookupInvokable(intercessionHandler);
    return handler.invoke(domain, false, new Object[] {domain, fieldIndex, obj});
  }

  @Override
  public void executeVoid(final VirtualFrame frame) {
    executeGeneric(frame);
  }
}
