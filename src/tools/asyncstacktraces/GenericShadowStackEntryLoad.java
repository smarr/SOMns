package tools.asyncstacktraces;

import som.interpreter.SArguments;
import som.interpreter.nodes.ExpressionNode;


public class GenericShadowStackEntryLoad extends ShadowStackEntryLoad {

  @Override
  protected void loadShadowStackEntry(final Object[] arguments,
      final ExpressionNode expression,
      final ShadowStackEntry prevEntry, final ShadowStackEntryLoad firstShadowStackEntryLoad,
      final boolean async) {
    setShadowStackEntry(SArguments.instantiateShadowStackEntry(prevEntry, expression, async),
        arguments);
  }

  @Override
  public int getCurrentCacheSize() {
    return 0;
  }

}
