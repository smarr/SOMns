package som.interpreter.nodes.enforced;

import som.interpreter.nodes.FieldNode.AbstractFieldWriteNode;
import som.vm.Universe;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class EnforcedFieldWriteNode extends AbstractFieldWriteNode {
  private final long fieldIndex;
  private final long somFieldIndex;
  private final SSymbol intercessionHandler;

  public EnforcedFieldWriteNode(final long fieldIndex, final SourceSection source) {
    super(source, true);
    this.fieldIndex = fieldIndex;
    this.somFieldIndex = fieldIndex + 1;
    intercessionHandler = Universe.current().symbolFor("write:toField:of:");
  }

  public EnforcedFieldWriteNode(final EnforcedFieldWriteNode node) {
    this(node.fieldIndex, node.getSourceSection());
  }

  @Specialization
  public final Object doSObject(final SObject obj, final Object value) {
    CompilerAsserts.neverPartOfCompilation();
    SObject domain = obj.getDomain();
    SInvokable handler = domain.getSOMClass(null).lookupInvokable(intercessionHandler);
    return handler.invoke(domain, false, new Object[] {domain, value, somFieldIndex, obj});
  }

  @Override
  public final void executeVoid(final VirtualFrame frame) {
    executeGeneric(frame);
  }
}
