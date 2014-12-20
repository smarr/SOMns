package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.interpreter.nodes.dispatch.AbstractDispatchNode.AbstractCachedDispatchNode;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;


public final class CachedDnuSimpleCheckNode extends AbstractCachedDispatchNode {
  private final Class<?> expectedClass;
  private final SSymbol selector;

  public CachedDnuSimpleCheckNode(final Class<?> rcvrClass,
      final SClass rcvrSClass,
      final SSymbol selector, final AbstractDispatchNode nextInCache) {
    super(rcvrSClass.lookupInvokable(
        Universe.current().symbolFor("doesNotUnderstand:arguments:")).
          getCallTarget(),
      nextInCache);
    expectedClass = rcvrClass;
    this.selector = selector;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    Object rcvr = arguments[0];
    if (rcvr.getClass() == expectedClass) {
      Object[] argsArr = new Object[] {
          rcvr, selector, SArguments.getArgumentsWithoutReceiver(arguments) };
      return cachedMethod.call(frame, argsArr);
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }
}
