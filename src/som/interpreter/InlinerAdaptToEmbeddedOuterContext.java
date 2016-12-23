package som.interpreter;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.Variable.Argument;
import som.inlining.InliningVisitor;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SOMNode;


public final class InlinerAdaptToEmbeddedOuterContext extends InliningVisitor {

  public static ExpressionNode doInline(final ExpressionNode body,
      final InlinerForLexicallyEmbeddedMethods inliner,
      final MethodScope currentMethodScope) {
    ExpressionNode inlinedBody = NodeUtil.cloneNode(body);

    return NodeVisitorUtil.applyVisitor(inlinedBody,
        new InlinerAdaptToEmbeddedOuterContext(inliner, 1,
            currentMethodScope));
  }

  public static ExpressionNode doInline(final ExpressionNode body,
      final InlinerAdaptToEmbeddedOuterContext inliner,
      final MethodScope currentMethodScope) {
    ExpressionNode inlinedBody = NodeUtil.cloneNode(body);

    return NodeVisitorUtil.applyVisitor(inlinedBody,
        new InlinerAdaptToEmbeddedOuterContext(inliner.outerInliner,
            inliner.contextLevel + 1, currentMethodScope));
  }

  private final InlinerForLexicallyEmbeddedMethods outerInliner;

  // this inliner refers to the block at the contextLevel given here, and
  // thus, needs to apply its transformations to elements referring to that
  // level
  private final int contextLevel;

  private InlinerAdaptToEmbeddedOuterContext(
      final InlinerForLexicallyEmbeddedMethods outerInliner,
      final int appliesToContextLevel,
      final MethodScope scope) {
    super(scope);
    this.outerInliner = outerInliner;
    this.contextLevel = appliesToContextLevel;
  }

  public MethodScope getCurrentMethodScope() {
    return scope;
  }

  public MethodScope getScope(final Method method) {
    return scope.getScope(method);
  }

  /*
   * if the inliner applies to this level, the node needs to be adapted to
   * refer to frame slots that got embedded
   */
  public boolean appliesTo(final int contextLevel) {
    return this.contextLevel == contextLevel;
  }

  public boolean needToAdjustLevel(final int contextLevel) {
    return this.contextLevel < contextLevel;
  }

  @Override
  public boolean visit(final Node node) {
    if (node instanceof SOMNode) {
      ((SOMNode) node).replaceWithCopyAdaptedToEmbeddedOuterContext(this);
    }
    return true;
  }

  public ExpressionNode getReplacementForBlockArgument(final Argument arg,
      final SourceSection sourceSection) {
    return outerInliner.getReplacementForNonLocalArgument(arg, contextLevel,
        sourceSection);
  }
}
