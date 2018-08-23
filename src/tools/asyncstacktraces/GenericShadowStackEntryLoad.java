package tools.asyncstacktraces;

import som.interpreter.SArguments;
import som.interpreter.nodes.ExpressionNode;


public class GenericShadowStackEntryLoad extends ShadowStackEntryLoad {

  protected ShadowStackEntry expectedShadowStackEntry;
  protected ShadowStackEntry cachedShadowStackEntry;

  public GenericShadowStackEntryLoad(final ShadowStackEntry prevEntry,
      final ShadowStackEntry newEntry) {
    this.expectedShadowStackEntry = prevEntry;
    this.cachedShadowStackEntry = newEntry;
  }

  @Override
  protected void loadShadowStackEntry(final Object[] arguments,
      final ExpressionNode expression,
      final ShadowStackEntry prevEntry, final ShadowStackEntryLoad firstShadowStackEntryLoad,
      final boolean async) {
    if (prevEntry != expectedShadowStackEntry) {
      expectedShadowStackEntry = prevEntry;
      cachedShadowStackEntry =
          SArguments.instantiateShadowStackEntry(prevEntry, expression, async);
      if (ANALYSIS) {
        megaMiss++;
      }
    } else {
      if (ANALYSIS) {
        megaCacheHit++;
      }
    }
    setShadowStackEntry(cachedShadowStackEntry, arguments);
  }

  @Override
  public int getCurrentCacheSize() {
    return 0;
  }

}
