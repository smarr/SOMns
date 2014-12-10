package som.interpreter.nodes.dispatch;

import som.interpreter.nodes.dispatch.AbstractDispatchNode.AbstractCachedDispatchNode;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class CachedDispatchSObjectCheckNode extends AbstractCachedDispatchNode {

  private final SClass expectedClass;

  public CachedDispatchSObjectCheckNode(final SClass rcvrClass,
      final CallTarget callTarget, final AbstractDispatchNode nextInCache) {
    super(callTarget, nextInCache);
    this.expectedClass = rcvrClass;
  }

  @Override
  public Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments) {
    SObject rcvr = (SObject) arguments[0];
    if (rcvr.getSOMClass() == expectedClass) {
      return cachedMethod.call(frame, arguments);
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }
}
