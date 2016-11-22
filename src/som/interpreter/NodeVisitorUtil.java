package som.interpreter;

import com.oracle.truffle.api.nodes.NodeVisitor;

import som.interpreter.nodes.DummyParent;
import som.interpreter.nodes.ExpressionNode;


public final class NodeVisitorUtil {

  public static ExpressionNode applyVisitor(final ExpressionNode body,
      final NodeVisitor visitor) {
    DummyParent dummyParent = new DummyParent(body);

    body.accept(visitor);

    // need to return the child of the dummy parent,
    // since it could have been replaced
    return (ExpressionNode) dummyParent.child;
  }
}
