package som.interpreter.nodes.nary;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import bd.nodes.EagerlySpecializable;
import bd.nodes.PreevaluatedExpression;
import som.VM;
import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;


public abstract class EagerlySpecializableNode extends ExprWithTagsNode
    implements PreevaluatedExpression, EagerlySpecializable<ExpressionNode, SSymbol, VM> {

  @CompilationFinal private boolean eagerlyWrapped;

  @Override
  public ExpressionNode initialize(final SourceSection sourceSection,
      final boolean eagerlyWrapped) {
    this.initialize(sourceSection);
    assert !this.eagerlyWrapped;
    this.eagerlyWrapped = eagerlyWrapped;
    return this;
  }

  /**
   * This method is used by eager wrapper or if this node is not eagerly
   * wrapped.
   */
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    return super.isTaggedWith(tag);
  }

  @Override
  protected final boolean isTaggedWith(final Class<?> tag) {
    if (eagerlyWrapped) {
      return false;
    } else {
      return isTaggedWithIgnoringEagerness(tag);
    }
  }

  @Override
  protected void onReplace(final Node newNode, final CharSequence reason) {
    if (newNode instanceof WrapperNode ||
        !(newNode instanceof EagerlySpecializableNode)) {
      return;
    }

    EagerlySpecializableNode n = (EagerlySpecializableNode) newNode;
    n.eagerlyWrapped = eagerlyWrapped;
    super.onReplace(newNode, reason);
  }
}
