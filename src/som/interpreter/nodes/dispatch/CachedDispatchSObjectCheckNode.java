package som.interpreter.nodes.dispatch;

import som.interpreter.nodes.dispatch.AbstractDispatchNode.AbstractCachedDispatchNode;
import som.interpreter.objectstorage.ClassFactory;
import som.vmobjects.SObjectWithoutFields;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class CachedDispatchSObjectCheckNode extends AbstractCachedDispatchNode {

  private final ClassFactory expectedClassFactory;

  public CachedDispatchSObjectCheckNode(final ClassFactory rcvrFactory,
      final CallTarget callTarget, final AbstractDispatchNode nextInCache) {
    super(callTarget, nextInCache);
    this.expectedClassFactory = rcvrFactory;
  }

  @Override
  public Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments) {
    SObjectWithoutFields rcvr = (SObjectWithoutFields) arguments[0];
    if (rcvr.getSOMClass().getFactory() == expectedClassFactory) {
      return cachedMethod.call(frame, arguments);
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }
}
