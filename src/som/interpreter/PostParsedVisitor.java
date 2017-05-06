package som.interpreter;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;

import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode.AbstractUninitializedMessageSendNode;
import som.primitives.BlockPrimsFactory.ValueNonePrimFactory;

public final class PostParsedVisitor implements NodeVisitor {

  InliningVisitor             inline;
  public final int            contextLevel;
  protected final MethodScope scope;

  public static ExpressionNode doInline(final ExpressionNode body,
      final MethodScope inlinedCurrentScope, final int appliesTo) {

    return NodeVisitorUtil.applyVisitor(body,
        new PostParsedVisitor(inlinedCurrentScope, appliesTo));
  }

  private PostParsedVisitor(final MethodScope scope, final int appliesTo) {
    this.scope = scope;
    this.contextLevel = appliesTo;
  }

  @Override
  public boolean visit(final Node node) {

    if (node instanceof AbstractUninitializedMessageSendNode) {
      AbstractUninitializedMessageSendNode msgSend = (AbstractUninitializedMessageSendNode) node;

      if (msgSend.getSelector().getString().equals("spawn:")) {
        ExpressionNode[] exp = msgSend.getArguments();

        Node replacement = ValueNonePrimFactory.create(false,
            node.getSourceSection(), exp[1]);
        node.replace(replacement);

      }

      else if (msgSend.getSelector().getString().equals("join")) {
        ExpressionNode[] exp = msgSend.getArguments();
        node.replace(exp[0]);
      }
    }

    return true;

  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
