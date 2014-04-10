package som.interpreter.nodes.dispatch;

import som.vmobjects.SInvokable;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.CallNode;
import com.oracle.truffle.api.nodes.Node;


public abstract class AbstractDispatchNode extends Node {
  protected static final int INLINE_CACHE_SIZE = 6;

  public abstract Object executeDispatch(Object[] arguments);

  public abstract static class AbstractCachedDispatchNode
      extends AbstractDispatchNode {

    @Child protected CallNode             cachedMethod;
    @Child protected AbstractDispatchNode nextInCache;

    public AbstractCachedDispatchNode(final SInvokable method,
        final AbstractDispatchNode nextInCache) {
      CallTarget methodCallTarget = method.getCallTarget();
      CallNode   cachedMethod     = Truffle.getRuntime().createCallNode(methodCallTarget);

      this.cachedMethod = cachedMethod;
      this.nextInCache  = nextInCache;
    }
  }
}
