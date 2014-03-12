package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.interpreter.nodes.dispatch.AbstractDispatchNode.AbstractCachedDispatchNode;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.frame.VirtualFrame;


public class CachedDispatchSimpleCheckNode extends AbstractCachedDispatchNode {

  private final Class<?>     expectedClass;

  public CachedDispatchSimpleCheckNode(final Class<?> rcvrClass,
      final SInvokable method, final AbstractDispatchNode nextInCache) {
    super(method, nextInCache);
    this.expectedClass = rcvrClass;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object rcvr,
      final Object[] arguments) {
    if (rcvr.getClass() == expectedClass) {
      return cachedMethod.call(frame.pack(), new SArguments(rcvr, arguments));
    } else {
      return nextInCache.executeDispatch(frame, rcvr, arguments);
    }
  }
}
