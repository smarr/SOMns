package som.interpreter.nodes.dispatch;

import java.util.HashMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import som.instrumentation.CountingDirectCallNode;
import som.interpreter.Invokable;
import som.vm.VmSettings;


public final class CachedDispatchNode extends AbstractDispatchNode {

  @Child private DirectCallNode       cachedMethod;
  @Child private AbstractDispatchNode nextInCache;

  private final DispatchGuard guard;

  public CachedDispatchNode(final CallTarget methodCallTarget,
      final DispatchGuard guard, final AbstractDispatchNode nextInCache) {
    super(nextInCache.getSourceSection());
    this.guard = guard;
    this.nextInCache = nextInCache;
    this.cachedMethod = Truffle.getRuntime().createDirectCallNode(methodCallTarget);
    if (VmSettings.DYNAMIC_METRICS) {
      this.cachedMethod = new CountingDirectCallNode(this.cachedMethod);
    }
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    try {
      if (guard.entryMatches(arguments[0])) {
        return cachedMethod.call(arguments);
      } else {
        return nextInCache.executeDispatch(frame, arguments);
      }
    } catch (InvalidAssumptionException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return replace(nextInCache).executeDispatch(frame, arguments);
    }
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }

  @Override
  public void collectDispatchStatistics(final HashMap<Invokable, Integer> result) {
    CountingDirectCallNode node = (CountingDirectCallNode) this.cachedMethod;
    result.put(node.getInvokable(), node.getCount());
    nextInCache.collectDispatchStatistics(result);
  }
}
