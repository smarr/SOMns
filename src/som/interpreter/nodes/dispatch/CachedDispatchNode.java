package som.interpreter.nodes.dispatch;

import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import som.instrumentation.CountingDirectCallNode;
import som.interpreter.Invokable;
import som.vm.VmSettings;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.impl.DefaultCallTarget;
import som.interpreter.Invokable;
import tools.debugger.asyncstacktraces.ShadowStackEntryLoad;


public final class CachedDispatchNode extends AbstractDispatchNode implements BackCacheCallNode {
  private final Assumption            stillUniqueCaller;
  @Child private DirectCallNode       cachedMethod;
  @Child private AbstractDispatchNode nextInCache;
  @CompilationFinal private boolean   uniqueCaller;

  @Child protected ShadowStackEntryLoad shadowStackEntryLoad = ShadowStackEntryLoad.create();

  private final DispatchGuard guard;

  public CachedDispatchNode(final CallTarget methodCallTarget,
                            final DispatchGuard guard, final AbstractDispatchNode nextInCache) {
    this(methodCallTarget, guard, nextInCache, true);
  }

  public CachedDispatchNode(final CallTarget methodCallTarget,
      final DispatchGuard guard, final AbstractDispatchNode nextInCache, final boolean defaultUniqueCaller) {
    super(nextInCache.getSourceSection());
    stillUniqueCaller = Truffle.getRuntime().createAssumption();
    this.guard = guard;
    this.nextInCache = nextInCache;
    this.cachedMethod = Truffle.getRuntime().createDirectCallNode(methodCallTarget);
    if (VmSettings.DYNAMIC_METRICS) {
      this.cachedMethod = new CountingDirectCallNode(this.cachedMethod);
    }
    if (defaultUniqueCaller) {
      BackCacheCallNode.initializeUniqueCaller((RootCallTarget) methodCallTarget, this);
    }
  }

  public CachedDispatchNode(final CachedDispatchNode node, final boolean uniqueCaller) {
      this(node.cachedMethod.getCallTarget(), node.guard, node.nextInCache, false);
      this.uniqueCaller = uniqueCaller;
    }

    @Override
    public void makeUniqueCaller() {
      uniqueCaller = true;
    }

    @Override
    public void makeMultipleCaller() {
      uniqueCaller = false;
      stillUniqueCaller.invalidate();
    }

    @Override
    public Invokable getCachedMethod() {
      RootCallTarget ct = (DefaultCallTarget) cachedMethod.getCallTarget();
      return (Invokable) ct.getRootNode();
    }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    try {
      if (guard.entryMatches(arguments[0])) {
        stillUniqueCaller.check();
        BackCacheCallNode.setShadowStackEntry(frame,
                uniqueCaller, arguments, this, shadowStackEntryLoad);
        return cachedMethod.call(arguments);
      } else {
        return nextInCache.executeDispatch(frame, arguments);
      }
    } catch (InvalidAssumptionException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      if (stillUniqueCaller.isValid()) {
        return replace(nextInCache).executeDispatch(frame, arguments);
      } else {
        return replace(new CachedDispatchNode(this, false)).executeDispatch(frame, arguments);
      }
    }
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }

  @Override
  public void collectDispatchStatistics(final Map<Invokable, Integer> result) {
    CountingDirectCallNode node = (CountingDirectCallNode) this.cachedMethod;
    result.put(node.getInvokable(), node.getCount());
    nextInCache.collectDispatchStatistics(result);
  }
}
