package som.interpreter;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SOMNode;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.SourceSection;


public class InlinerAdaptToEmbeddedOuterContext implements NodeVisitor {

  public static ExpressionNode doInline(final ExpressionNode body,
      final InlinerForLexicallyEmbeddedMethods inliner) {
    ExpressionNode inlinedBody = NodeUtil.cloneNode(body);

    return NodeVisitorUtil.applyVisitor(inlinedBody,
        new InlinerAdaptToEmbeddedOuterContext(inliner, 1));
  }

  public static ExpressionNode doInline(final ExpressionNode body,
      final InlinerAdaptToEmbeddedOuterContext inliner) {
    ExpressionNode inlinedBody = NodeUtil.cloneNode(body);

    return NodeVisitorUtil.applyVisitor(inlinedBody,
        new InlinerAdaptToEmbeddedOuterContext(inliner.outerInliner,
            inliner.contextLevel + 1));
  }

  private final InlinerForLexicallyEmbeddedMethods outerInliner;

  // this inliner refers to the block at the contextLevel given here, and
  // thus, needs to apply its transformations to elements referring to that
  // level
  private final int contextLevel;

  private InlinerAdaptToEmbeddedOuterContext(
      final InlinerForLexicallyEmbeddedMethods outerInliner,
      final int appliesToContextLevel) {
    this.outerInliner = outerInliner;
    this.contextLevel = appliesToContextLevel;
  }

  public FrameSlot getOuterSlot(final Object slotId) {
    return outerInliner.getLocalSlot(slotId);
  }

  public LexicalContext getOuterContext() {
    return outerInliner.getLexicalContext();
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
