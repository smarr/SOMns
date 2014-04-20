package som.interpreter.nodes.dispatch;

import som.interpreter.nodes.dispatch.AbstractDispatchNode.AbstractCachedDispatchNode;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.frame.VirtualFrame;


public final class CachedDispatchSimpleCheckNode extends AbstractCachedDispatchNode {

  private final Class<?> expectedClass;

  public CachedDispatchSimpleCheckNode(final Class<?> rcvrClass,
      final SInvokable method, final AbstractDispatchNode nextInCache) {
    super(method, nextInCache);
    this.expectedClass = rcvrClass;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame,
      final Object[] arguments) {
    if (arguments[0].getClass() == expectedClass) {
      return cachedMethod.call(frame, arguments);
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }

  public static final class CachedDispatchTrueCheckNode
      extends AbstractCachedDispatchNode {
    public CachedDispatchTrueCheckNode(final SInvokable method,
        final AbstractDispatchNode nextInCache) {
      super(method, nextInCache);
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object[] arguments) {
      if (arguments[0] == Boolean.TRUE) {
        return cachedMethod.call(frame, arguments);
      } else {
        return nextInCache.executeDispatch(frame, arguments);
      }
    }
  }

  public static final class CachedDispatchFalseCheckNode
      extends AbstractCachedDispatchNode {
    public CachedDispatchFalseCheckNode(final SInvokable method,
        final AbstractDispatchNode nextInCache) {
      super(method, nextInCache);
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object[] arguments) {
      if (arguments[0] == Boolean.FALSE) {
        return cachedMethod.call(frame, arguments);
      } else {
        return nextInCache.executeDispatch(frame, arguments);
      }
    }
  }
}
