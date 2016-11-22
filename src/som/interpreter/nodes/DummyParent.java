package som.interpreter.nodes;

import com.oracle.truffle.api.nodes.Node;

/** Dummy Node to work around restrictions that a node that is going to
 * be instrumented, needs to have a parent. */
public final class DummyParent extends Node {
  @Child public Node child;

  public DummyParent(final Node node) {
    this.child = insert(node);
  }
}
