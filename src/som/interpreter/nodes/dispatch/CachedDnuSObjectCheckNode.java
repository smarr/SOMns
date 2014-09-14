package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.interpreter.nodes.dispatch.AbstractDispatchNode.AbstractCachedDispatchNode;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class CachedDnuSObjectCheckNode extends AbstractCachedDispatchNode {
  private final SClass expectedClass;
  private final SSymbol selector;

  public CachedDnuSObjectCheckNode(final SClass rcvrClass,
      final SSymbol selector, final AbstractDispatchNode nextInCache) {
    super(rcvrClass.lookupInvokable(
        Universe.current().symbolFor("doesNotUnderstand:arguments:")).
          getCallTarget(),
      nextInCache);
    expectedClass = rcvrClass;
    this.selector = selector;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    SObject rcvr = CompilerDirectives.unsafeCast(arguments[0], SObject.class, true, true);

    if (rcvr.getSOMClass() == expectedClass) {
      Object[] argsArr = new Object[] {
          rcvr, selector, SArguments.getArgumentsWithoutReceiver(arguments) };
      return cachedMethod.call(frame, argsArr);
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }
}
