package som.interpreter;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;

import som.compiler.MethodBuilder;
import som.inlining.InliningVisitor;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SOMNode;


public class InlinerForLexicallyEmbeddedMethods extends InliningVisitor {

  public static ExpressionNode doInline(
      final ExpressionNode body, final MethodBuilder builder) {
    ExpressionNode inlinedBody = NodeUtil.cloneNode(body);

    return NodeVisitorUtil.applyVisitor(inlinedBody,
        new InlinerForLexicallyEmbeddedMethods(builder));
  }

  private final MethodBuilder builder;

  public InlinerForLexicallyEmbeddedMethods(final MethodBuilder builder) {
    super(builder.getCurrentMethodScope());
    this.builder = builder;
  }

  @Override
  public boolean visit(final Node node) {
    if (node instanceof SOMNode) {
      ((SOMNode) node).replaceWithLexicallyEmbeddedNode(this);
    }
    return true;
  }

  public MethodScope getCurrentMethodScope() {
    return builder.getCurrentMethodScope();
  }

  public MethodScope getScope(final Method method) {
    MethodScope root = builder.getCurrentMethodScope();
    assert root == scope;
    return root.getScope(method);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + builder.getSignature() + "]";
  }
}
