package som.interpreter;

import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.ContextualNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SOMNode;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;


public final class SplitterForLexicallyEmbeddedCode implements NodeVisitor {

  public static ExpressionNode doInline(
      final ExpressionNode body,
      final MethodScope inlinedCurrentScope) {
    ExpressionNode inlinedBody = NodeUtil.cloneNode(body);

    return NodeVisitorUtil.applyVisitor(inlinedBody,
        new SplitterForLexicallyEmbeddedCode(inlinedCurrentScope));
  }

  private final MethodScope inlinedCurrentScope;

  private SplitterForLexicallyEmbeddedCode(final MethodScope inlinedCurrentScope) {
    this.inlinedCurrentScope = inlinedCurrentScope;
  }

  public MethodScope getCurrentScope() {
    return inlinedCurrentScope;
  }

  @Override
  public boolean visit(final Node node) {
    prepareBodyNode(node);
    assert !(node instanceof Method);
    return true;
  }

  public FrameSlot getLocalFrameSlot(final Object slotId) {
    return inlinedCurrentScope.getFrameDescriptor().findFrameSlot(slotId);
  }

  public FrameSlot getFrameSlot(final ContextualNode node, final Object slotId) {
    return getFrameSlot(slotId, node.getContextLevel());
  }

  public FrameSlot getFrameSlot(final Object slotId, int level) {
    MethodScope ctx = inlinedCurrentScope;
    while (level > 0) {
      ctx = ctx.getOuterMethodScope();
      level--;
    }
    return ctx.getFrameDescriptor().findFrameSlot(slotId);
  }

  private void prepareBodyNode(final Node node) {
    if (node instanceof SOMNode) {
      ((SOMNode) node).replaceWithIndependentCopyForInlining(this);
    }
  }
}
