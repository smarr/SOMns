package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.interpreter.nodes.dispatch.AbstractDispatchNode.AbstractCachedDispatchNode;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class CachedDispatchSObjectCheckNode extends AbstractCachedDispatchNode {

  private final SClass expectedClass;

  public CachedDispatchSObjectCheckNode(final SClass rcvrClass,
      final CallTarget callTarget, final AbstractDispatchNode nextInCache) {
    super(callTarget, nextInCache);
    this.expectedClass = rcvrClass;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final SObject domain,
      final boolean enforced, final Object[] arguments) {
    SObject rcvr = CompilerDirectives.unsafeCast(arguments[0], SObject.class, true);
    if (rcvr.getSOMClass(null) == expectedClass) {
      return cachedMethod.call(frame, SArguments.createSArguments(domain, enforced, arguments));
    } else {
      return nextInCache.executeDispatch(frame, domain, enforced, arguments);
    }
  }
}
