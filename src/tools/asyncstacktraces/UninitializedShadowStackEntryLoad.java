package tools.asyncstacktraces;

import som.interpreter.SArguments;
import som.interpreter.nodes.ExpressionNode;


public class UninitializedShadowStackEntryLoad extends ShadowStackEntryLoad {

  @Override
  protected void loadShadowStackEntry(final Object[] arguments,
      final ExpressionNode expression,
      final ShadowStackEntry prevEntry,
      final ShadowStackEntryLoad firstShadowStackEntryLoad,
      final boolean async) {
    ShadowStackEntry newEntry =
        SArguments.instantiateShadowStackEntry(prevEntry, expression, async);
    ShadowStackEntryLoad newLoad;
    if (firstShadowStackEntryLoad.getCurrentCacheSize() > NUM_SHADOW_STACK_ENTRIES) {
      newLoad = new GenericShadowStackEntryLoad();
      // firstShadowStackEntryLoad.replace(newLoad);
      replace(newLoad);
    } else {
      newLoad = new CachedShadowStackEntryLoad(prevEntry, newEntry);
      replace(newLoad);
    }
    newLoad.loadShadowStackEntry(arguments, expression, prevEntry,
        firstShadowStackEntryLoad, async);
  }

  @Override
  public int getCurrentCacheSize() {
    return 0;
  }

}
