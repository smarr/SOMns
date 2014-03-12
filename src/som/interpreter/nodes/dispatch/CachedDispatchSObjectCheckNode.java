package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.interpreter.nodes.dispatch.AbstractDispatchNode.AbstractCachedDispatchNode;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;


public class CachedDispatchSObjectCheckNode extends AbstractCachedDispatchNode {

  private final SClass expectedClass;

  public CachedDispatchSObjectCheckNode(final SClass rcvrClass,
      final SInvokable method, final AbstractDispatchNode nextInCache) {
    super(method, nextInCache);
    this.expectedClass = rcvrClass;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object receiver,
      final Object[] arguments) {
    SObject rcvr = CompilerDirectives.unsafeCast(receiver, SObject.class, true);
    if (rcvr.getSOMClass(null) == expectedClass) {
      return cachedMethod.call(frame.pack(), new SArguments(receiver, arguments));
    } else {
      return nextInCache.executeDispatch(frame, receiver, arguments);
    }
  }
}
