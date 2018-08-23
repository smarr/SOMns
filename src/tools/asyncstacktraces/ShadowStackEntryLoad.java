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
  public static int           cacheMiss    = 0;

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
}
