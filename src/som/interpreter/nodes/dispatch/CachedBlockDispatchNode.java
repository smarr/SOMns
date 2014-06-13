package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.interpreter.nodes.dispatch.AbstractDispatchNode.AbstractCachedDispatchNode;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;

import com.oracle.truffle.api.frame.VirtualFrame;


public final class CachedBlockDispatchNode extends AbstractCachedDispatchNode {

  private final SInvokable cachedSomMethod;

  public CachedBlockDispatchNode(final SInvokable method,
      final AbstractDispatchNode nextInCache) {
    super(method, nextInCache);
    this.cachedSomMethod = method;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    SBlock rcvr = (SBlock) arguments[0];
    if (rcvr.getMethod() == cachedSomMethod) {
      SObject domain = SArguments.domain(frame);
      boolean enforced = SArguments.enforced(frame);
      return cachedMethod.call(frame, SArguments.createSArguments(domain, enforced, arguments));
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }
}
