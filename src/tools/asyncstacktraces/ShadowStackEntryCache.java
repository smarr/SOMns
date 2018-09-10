package tools.asyncstacktraces;

import java.util.HashSet;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import som.interpreter.SArguments;
import som.interpreter.nodes.ExpressionNode;
import som.vm.VmSettings;


public class ShadowStackEntryCache {

  // *** Cache settings ***
  // If true, on cache miss & cache full override one of the previous entry, deterministically.
  public static final boolean DYNAMIC_REWRITE          = true;
  public static final int     SHADOW_STACK_ENTRY_SIZE  = 2;
  public static final int     NUM_SHADOW_STACK_ENTRIES = 4;

  public static final ShadowStackEntry DEFAULT_ENTRY = ShadowStackEntry.create(null, null);

  // *** Analysis variables (optional) ***
  public static final boolean           ANALYSIS             = false;
  public static int                     cacheHit             = 0;
  public static int                     cacheRewrite         = 0;
  public static int                     dynamicCacheRewrite  = 0;
  public static int                     megamorphicCacheMiss = 0;
  public static HashSet<ExpressionNode> failedSendSites      =
      new HashSet<ExpressionNode>();
  public static HashSet<ExpressionNode> sendSites            =
      new HashSet<ExpressionNode>();

  // *** cache specific data ***
  protected final ShadowStackEntry[] cache =
      new ShadowStackEntry[NUM_SHADOW_STACK_ENTRIES * SHADOW_STACK_ENTRY_SIZE];
  // specific to dynamic rewrite
  protected int rewriteIndex = 0;

  public static ShadowStackEntryCache createShadowStackEntryCache() {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      return new ShadowStackEntryCache();
    } else {
      return null;
    }
  }

  public ShadowStackEntryCache() {
    // Avoid cache hit on null
    for (int i = 0; i < cache.length; i += SHADOW_STACK_ENTRY_SIZE) {
      cache[i] = DEFAULT_ENTRY;
    }
  }

  @ExplodeLoop
  public void lookupInCache(final Object[] arguments,
      final ExpressionNode expression,
      final VirtualFrame frame,
      final boolean async) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      if (ANALYSIS) {
        sendSites.add(expression);
      }

      ShadowStackEntry previousStackEntry = SArguments.getShadowStackEntry(frame);

      // cache hit - most common case
      for (int i = 0; i < cache.length; i += SHADOW_STACK_ENTRY_SIZE) {
        ShadowStackEntry previousCachedEntry = cache[i];
        if (previousCachedEntry == previousStackEntry) {
          arguments[arguments.length - 1] = cache[i + 1];
          if (ANALYSIS) {
            cacheHit++;
          }
          return;
        }
      }

      // cache miss & rewrite cache
      for (int i = 0; i < cache.length; i += SHADOW_STACK_ENTRY_SIZE) {
        ShadowStackEntry previousCachedEntry = cache[i];
        if (previousCachedEntry == DEFAULT_ENTRY) {
          ShadowStackEntry newShadowStackEntry =
              SArguments.instantiateShadowStackEntry(previousStackEntry, expression, async);
          arguments[arguments.length - 1] = newShadowStackEntry;
          cache[i] = previousStackEntry;
          cache[i + 1] = newShadowStackEntry;
          if (ANALYSIS) {
            cacheRewrite++;
          }
          return;
        }
      }

      // cache miss & not enough room to rewrite
      if (ANALYSIS) {
        megamorphicCacheMiss++;
      }
      if (!DYNAMIC_REWRITE) {
        arguments[arguments.length - 1] =
            SArguments.instantiateShadowStackEntry(previousStackEntry, expression, async);
      } else {
        ShadowStackEntry newShadowStackEntry =
            SArguments.instantiateShadowStackEntry(previousStackEntry, expression, async);
        arguments[arguments.length - 1] = newShadowStackEntry;
        cache[rewriteIndex] = previousStackEntry;
        cache[rewriteIndex + 1] = newShadowStackEntry;
        rewriteIndex = rewriteIndex++ % NUM_SHADOW_STACK_ENTRIES;
        if (ANALYSIS) {
          failedSendSites.add(expression);
          dynamicCacheRewrite++;
        }
        return;
      }

    }
  }
}
