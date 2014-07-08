package som.interpreter.nodes.dispatch;

import som.vmobjects.SInvokable;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;


public abstract class AbstractDispatchNode extends Node implements DispatchChain {
  public static final int INLINE_CACHE_SIZE = 6;

  public abstract Object executeDispatch(final VirtualFrame frame,
      SObject domain, boolean enforced, final Object[] arguments);

  public abstract static class AbstractCachedDispatchNode
      extends AbstractDispatchNode {

    @Child protected DirectCallNode       cachedMethod;
    @Child protected AbstractDispatchNode nextInCache;

    public AbstractCachedDispatchNode(final SInvokable method,
        final AbstractDispatchNode nextInCache) {
      CallTarget methodCallTarget = method.getCallTarget();
      DirectCallNode cachedMethod = Truffle.getRuntime().createDirectCallNode(methodCallTarget);

      this.cachedMethod = cachedMethod;
      this.nextInCache  = nextInCache;
    }

    @Override
    public final int lengthOfDispatchChain() {
      return 1 + nextInCache.lengthOfDispatchChain();
    }
  }
}
