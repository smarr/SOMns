package som.interpreter;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.Variable;
import som.inlining.InliningVisitor;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.ContextualNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SOMNode;


public final class SplitterForLexicallyEmbeddedCode extends InliningVisitor {

  public static ExpressionNode doInline(
      final ExpressionNode body,
      final MethodScope inlinedCurrentScope) {
    ExpressionNode inlinedBody = NodeUtil.cloneNode(body);

    return NodeVisitorUtil.applyVisitor(inlinedBody,
        new SplitterForLexicallyEmbeddedCode(inlinedCurrentScope));
  }

  private SplitterForLexicallyEmbeddedCode(final MethodScope scope) {
    super(scope);
  }

  /**
   * Get the already split scope for an embedded block.
   */
  public MethodScope getSplitScope(final SourceSection source) {
    return scope.getEmbeddedScope(source);
  }

  @Override
  public boolean visit(final Node node) {
    prepareBodyNode(node);
    assert !(node instanceof Method);
    return true;
  }

  public FrameSlot getLocalFrameSlot(final Variable var) {
    return scope.getFrameDescriptor().findFrameSlot(var);
  }

  public FrameSlot getFrameSlot(final ContextualNode node, final Variable var) {
    return getFrameSlot(var, node.getContextLevel());
  }

  public FrameSlot getFrameSlot(final Variable var, int level) {
    MethodScope ctx = scope;
    while (level > 0) {
      ctx = ctx.getOuterMethodScope();
      level--;
    }
    return ctx.getFrameDescriptor().findFrameSlot(var);
  }

  private void prepareBodyNode(final Node node) {
    if (node instanceof SOMNode) {
      ((SOMNode) node).replaceWithIndependentCopyForInlining(this);
    }
  }
}
