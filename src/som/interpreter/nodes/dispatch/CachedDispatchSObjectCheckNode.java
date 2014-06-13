package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.interpreter.nodes.dispatch.AbstractDispatchNode.AbstractCachedDispatchNode;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class CachedDispatchSObjectCheckNode extends AbstractCachedDispatchNode {

  private final SClass expectedClass;

  public CachedDispatchSObjectCheckNode(final SClass rcvrClass,
      final SInvokable method, final AbstractDispatchNode nextInCache,
      final boolean executesEnforced) {
    super(method, nextInCache, executesEnforced);
    this.expectedClass = rcvrClass;
  }

  @Override
  public Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments) {
    SObject rcvr = CompilerDirectives.unsafeCast(arguments[0], SObject.class, true);
    if (rcvr.getSOMClass(null) == expectedClass) {
      SObject domain = SArguments.domain(frame);
      return cachedMethod.call(frame, SArguments.createSArguments(domain, executesEnforced, arguments));
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }
}
