package som.interpreter.nodes.dispatch;

import som.interpreter.nodes.dispatch.AbstractDispatchNode.AbstractCachedDispatchNode;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.frame.VirtualFrame;


public final class CachedBlockDispatchNode extends AbstractCachedDispatchNode {

  private final SInvokable cachedSomMethod;

  public CachedBlockDispatchNode(final SInvokable method,
      final AbstractDispatchNode nextInCache) {
    super(method.getCallTargetIfAvailable(), nextInCache);
    this.cachedSomMethod = method;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    SBlock rcvr = (SBlock) arguments[0];
    if (rcvr.getMethod() == cachedSomMethod) {
      return cachedMethod.call(frame, arguments);
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }
}
