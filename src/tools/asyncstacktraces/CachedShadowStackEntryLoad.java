package tools.asyncstacktraces;

import som.interpreter.nodes.ExpressionNode;


public class CachedShadowStackEntryLoad extends ShadowStackEntryLoad {

  @Child protected ShadowStackEntryLoad nextInCache;
  protected final ShadowStackEntry      expectedShadowStackEntry;
  protected final ShadowStackEntry      cachedShadowStackEntry;

  public CachedShadowStackEntryLoad(final ShadowStackEntry prevEntry,
      final ShadowStackEntry newEntry) {
    this.expectedShadowStackEntry = prevEntry;
    this.cachedShadowStackEntry = newEntry;
    nextInCache = new UninitializedShadowStackEntryLoad();
  }

  @Override
  public int getCurrentCacheSize() {
    return 1 + nextInCache.getCurrentCacheSize();
  }

  @Override
  protected void loadShadowStackEntry(final Object[] arguments,
      final ExpressionNode expression,
      final ShadowStackEntry prevEntry, final ShadowStackEntryLoad firstShadowStackEntryLoad,
      final boolean async) {
    if (prevEntry == expectedShadowStackEntry) {
      setShadowStackEntry(cachedShadowStackEntry, arguments);
      if (ANALYSIS) {
        cacheHit++;
      }
    } else {
      nextInCache.loadShadowStackEntry(arguments, expression, prevEntry,
          firstShadowStackEntryLoad, async);
    }
  }

}
