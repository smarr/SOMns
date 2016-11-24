package som.interpreter.nodes.nary;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.PreevaluatedExpression;
import som.vm.VmSettings;
import som.vmobjects.SSymbol;
import tools.debugger.nodes.AbstractBreakpointNode;
import tools.debugger.nodes.BreakpointNodeGen;
import tools.debugger.nodes.DisabledBreakpointNode;
import tools.debugger.session.BreakpointEnabling;
import tools.debugger.session.SectionBreakpoint;

public abstract class EagerlySpecializableNode extends ExprWithTagsNode
  implements PreevaluatedExpression {

  @CompilationFinal private boolean eagerlyWrapped;

  protected EagerlySpecializableNode(final EagerlySpecializableNode wrappedNode) {
    super(wrappedNode);
    assert !wrappedNode.eagerlyWrapped : "I think this should be true.";
    this.eagerlyWrapped = false;
  }

  public EagerlySpecializableNode(final boolean eagerlyWrapped,
      final SourceSection source) {
    super(source);
    this.eagerlyWrapped = eagerlyWrapped;
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
        !(newNode instanceof EagerlySpecializableNode)) { return; }

    EagerlySpecializableNode n = (EagerlySpecializableNode) newNode;
    n.eagerlyWrapped = eagerlyWrapped;
    super.onReplace(newNode, reason);
  }

  /**
   * Create an eager primitive wrapper, which wraps this node.
   */
  public abstract EagerPrimitive wrapInEagerWrapper(
      final EagerlySpecializableNode prim, final SSymbol selector,
      final ExpressionNode[] arguments);

  /**
   * Create a breakpoint node that can be enable or disable.
   */
  public <T extends SectionBreakpoint> AbstractBreakpointNode createBreakpointNode(final SourceSection source, final BreakpointEnabling<T> bkp) {
    AbstractBreakpointNode node;

    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
        node = insert(BreakpointNodeGen.create(bkp));
    } else {
        node = insert(new DisabledBreakpointNode());
    }
    return node;
}
}
