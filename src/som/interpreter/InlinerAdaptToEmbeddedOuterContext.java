package som.interpreter;

import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SOMNode;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.SourceSection;


public final class InlinerAdaptToEmbeddedOuterContext implements NodeVisitor {

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

  private final MethodScope currentLexicalScope;

  private InlinerAdaptToEmbeddedOuterContext(
      final InlinerForLexicallyEmbeddedMethods outerInliner,
      final int appliesToContextLevel,
      final MethodScope currentLexicalContext) {
    this.outerInliner = outerInliner;
    this.contextLevel = appliesToContextLevel;
    this.currentLexicalScope = currentLexicalContext;
  }

  public FrameSlot getOuterSlot(final Object slotId) {
    return outerInliner.getLocalSlot(slotId);
  }

  public MethodScope getOuterContext() {
    return currentLexicalScope.getOuterMethodScope();
  }

  public MethodScope getCurrentMethodScope() {
    return currentLexicalScope;
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

  public ExpressionNode getReplacementForBlockArgument(final int argumentIndex,
      final SourceSection sourceSection) {
    return outerInliner.getReplacementForNonLocalArgument(contextLevel,
        argumentIndex, sourceSection);
  }
}
