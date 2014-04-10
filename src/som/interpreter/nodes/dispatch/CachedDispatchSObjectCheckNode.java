package som.interpreter.nodes.dispatch;

import som.interpreter.nodes.dispatch.AbstractDispatchNode.AbstractCachedDispatchNode;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;


public final class CachedDispatchSObjectCheckNode extends AbstractCachedDispatchNode {

  private final SClass expectedClass;

  public CachedDispatchSObjectCheckNode(final SClass rcvrClass,
      final SInvokable method, final AbstractDispatchNode nextInCache) {
    super(method, nextInCache);
    this.expectedClass = rcvrClass;
  }

  @Override
  public Object executeDispatch(final Object[] arguments) {
    SObject rcvr = CompilerDirectives.unsafeCast(arguments[0], SObject.class, true);
    if (rcvr.getSOMClass(null) == expectedClass) {
      return cachedMethod.call(arguments);
    } else {
      return nextInCache.executeDispatch(arguments);
    }
  }
}
