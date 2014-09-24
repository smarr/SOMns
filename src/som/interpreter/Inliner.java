package som.interpreter;

import som.interpreter.nodes.ContextualNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SOMNode;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;


public final class Inliner implements NodeVisitor {

  private static class DummyParent extends SOMNode {
    public DummyParent() { super(null); }
    @Child  private SOMNode child;

    @Override
    public ExpressionNode getFirstMethodBodyNode() { return null; }
    public void adopt(final SOMNode child) {
        this.child = insert(child);
    }
  }

  public static ExpressionNode doInline(
      final ExpressionNode body,
      final LexicalContext inlinedContext) {
    ExpressionNode inlinedBody = NodeUtil.cloneNode(body);

    DummyParent dummyParent = new DummyParent();
    dummyParent.adopt(inlinedBody);

    inlinedBody.accept(new Inliner(inlinedContext));
    return inlinedBody;
  }

  private final LexicalContext inlinedContext;

  private Inliner(final LexicalContext inlinedContext) {
    this.inlinedContext = inlinedContext;
  }

  public LexicalContext getLexicalContext() {
    return inlinedContext;
  }

  @Override
  public boolean visit(final Node node) {
    prepareBodyNode(node);
    assert !(node instanceof Method);
    return true;
  }

  public FrameSlot getLocalFrameSlot(final Object slotId) {
    return inlinedContext.getFrameDescriptor().findFrameSlot(slotId);
  }

  public FrameSlot getFrameSlot(final ContextualNode node, final Object slotId) {
    return getFrameSlot(slotId, node.getContextLevel());
  }

  public FrameSlot getFrameSlot(final Object slotId, int level) {
    LexicalContext ctx = inlinedContext;
    while (level > 0) {
      ctx = ctx.getOuterContext();
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
