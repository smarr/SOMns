package tools.asyncstacktraces;

import som.interpreter.nodes.ExpressionNode;


public class LocalShadowStackEntry extends ShadowStackEntry {

  public LocalShadowStackEntry(final ShadowStackEntry previousEntry,
      final ExpressionNode expression) {
    super(previousEntry, expression);
  }
}
