package bd.inlining;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;

import bd.basic.nodes.DummyParent;


final class NodeVisitorUtil {

  @SuppressWarnings("unchecked")
  public static <ExprT extends Node> ExprT applyVisitor(final ExprT body,
      final NodeVisitor visitor, final TruffleLanguage<?> language) {
    DummyParent dummyParent = new DummyParent(language, body);

    body.accept(visitor);

    // need to return the child of the dummy parent,
    // since it could have been replaced
    return (ExprT) dummyParent.child;
  }
}
