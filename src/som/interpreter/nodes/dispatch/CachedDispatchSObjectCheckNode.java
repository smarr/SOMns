package som.interpreter.nodes.dispatch;

import som.interpreter.nodes.dispatch.AbstractDispatchNode.AbstractCachedDispatchNode;
import som.interpreter.objectstorage.ClassFactory;
import som.vmobjects.SObjectWithClass;

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
    SObjectWithClass rcvr = (SObjectWithClass) arguments[0];

    if (rcvr.getFactory() == expectedClassFactory) {
      return cachedMethod.call(frame, arguments);
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }

  public static final class CachedDispatchSObjectWithoutFieldsCheckNode extends AbstractCachedDispatchNode {

    private final ClassFactory expectedClassFactory;

    public CachedDispatchSObjectWithoutFieldsCheckNode(
        final ClassFactory rcvrFactory, final CallTarget callTarget,
        final AbstractDispatchNode nextInCache) {
      super(callTarget, nextInCache);
      this.expectedClassFactory = rcvrFactory;
    }

    @Override
    public Object executeDispatch(
        final VirtualFrame frame, final Object[] arguments) {
      SObjectWithClass rcvr = (SObjectWithClass) arguments[0];

      if (rcvr.getFactory() == expectedClassFactory) {
        return cachedMethod.call(frame, arguments);
      } else {
        return nextInCache.executeDispatch(frame, arguments);
      }
    }
  }
}
