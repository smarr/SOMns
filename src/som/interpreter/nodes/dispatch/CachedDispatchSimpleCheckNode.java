package som.interpreter.nodes.dispatch;

import som.interpreter.nodes.dispatch.AbstractDispatchNode.AbstractCachedDispatchNode;
import som.vmobjects.SInvokable;


public final class CachedDispatchSimpleCheckNode extends AbstractCachedDispatchNode {

  private final Class<?> expectedClass;

  public CachedDispatchSimpleCheckNode(final Class<?> rcvrClass,
      final SInvokable method, final AbstractDispatchNode nextInCache) {
    super(method, nextInCache);
    this.expectedClass = rcvrClass;
  }

  @Override
  public Object executeDispatch(final Object[] arguments) {
    if (arguments[0].getClass() == expectedClass) {
      return cachedMethod.call(arguments);
    } else {
      return nextInCache.executeDispatch(arguments);
    }
  }
}
