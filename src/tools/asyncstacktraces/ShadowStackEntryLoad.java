package tools.asyncstacktraces;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

import som.interpreter.SArguments;
import som.interpreter.nodes.ExpressionNode;


public abstract class ShadowStackEntryLoad extends Node {
  public static final int NUM_SHADOW_STACK_ENTRIES = 6;

  public static final boolean ANALYSIS     = false;
  public static int           cacheHit     = 0;
  public static int           megaCacheHit = 0;
  public static int           megaMiss     = 0;

  public void loadShadowStackEntry(final Object[] arguments,
      final ExpressionNode expression,
      final VirtualFrame frame,
      final boolean async) {
    ShadowStackEntry prevEntry = SArguments.getShadowStackEntry(frame);
    loadShadowStackEntry(arguments, expression, prevEntry, this, async);
  }

  protected abstract void loadShadowStackEntry(Object[] arguments,
      ExpressionNode expression,
      ShadowStackEntry prevEntry,
      ShadowStackEntryLoad firstShadowStackEntryLoad,
      boolean async);

  public abstract int getCurrentCacheSize();

  protected void setShadowStackEntry(final ShadowStackEntry shadowStackEntry,
      final Object[] arguments) {
    arguments[arguments.length - 1] = shadowStackEntry;
  }

  public static final class UninitializedShadowStackEntryLoad extends ShadowStackEntryLoad {

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

  public static final class CachedShadowStackEntryLoad extends ShadowStackEntryLoad {

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

  public static final class GenericShadowStackEntryLoad extends ShadowStackEntryLoad {

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
}
