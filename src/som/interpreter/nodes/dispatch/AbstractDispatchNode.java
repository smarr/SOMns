package som.interpreter.nodes.dispatch;

import som.vmobjects.SInvokable;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;


public abstract class AbstractDispatchNode extends Node {
  public static final int INLINE_CACHE_SIZE = 6;

  protected final boolean executesEnforced;

  public AbstractDispatchNode(final boolean executesEnforced) {
    this.executesEnforced = executesEnforced;
  }

  public abstract Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments);

  public abstract int lengthOfDispatchChain();

  public abstract static class AbstractCachedDispatchNode
      extends AbstractDispatchNode {

    @Child protected DirectCallNode       cachedMethod;
    @Child protected AbstractDispatchNode nextInCache;

    public AbstractCachedDispatchNode(final SInvokable method,
        final AbstractDispatchNode nextInCache, final boolean executesEnforced) {
      super(executesEnforced);
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
