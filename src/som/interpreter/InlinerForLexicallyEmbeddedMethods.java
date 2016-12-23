package som.interpreter;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.MethodBuilder;
import som.compiler.Variable;
import som.compiler.Variable.Argument;
import som.compiler.Variable.Local;
import som.inlining.InliningVisitor;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableReadNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableWriteNode;


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

  public UninitializedVariableReadNode getLocalRead(final Variable var,
      final SourceSection source) {
    return (UninitializedVariableReadNode) getSplitVar(var).getReadNode(0, source);
  }

  public MethodScope getCurrentMethodScope() {
    return builder.getCurrentMethodScope();
  }

  public MethodScope getScope(final Method method) {
    MethodScope root = builder.getCurrentMethodScope();
    assert root == scope;
    return root.getScope(method);
  }

  public UninitializedVariableWriteNode getLocalWrite(final Local local,
      final ExpressionNode valExpr,
      final SourceSection source) {
    Local l = (Local) getSplitVar(local);
    return (UninitializedVariableWriteNode) l.getWriteNode(0, valExpr, source);
  }

  public ExpressionNode getReplacementForLocalArgument(final Argument arg,
      final SourceSection source) {
    return getSplitVar(arg).getReadNode(0, source);
  }

  public ExpressionNode getReplacementForNonLocalArgument(final Argument arg,
      final int contextLevel, final SourceSection source) {
    assert contextLevel > 0;
    return getSplitVar(arg).getReadNode(contextLevel, source);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + builder.getSignature() + "]";
  }
}
