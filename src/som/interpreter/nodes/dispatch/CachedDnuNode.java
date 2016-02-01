package som.interpreter.nodes.dispatch;

import som.VM;
import som.compiler.AccessModifier;
import som.interpreter.SArguments;
import som.vm.Symbols;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;


public final class CachedDnuNode extends AbstractDispatchNode {

  @Child private DirectCallNode       cachedMethod;
  @Child private AbstractDispatchNode nextInCache;

  private final DispatchGuard guard;
  private final SSymbol       selector;

  public CachedDnuNode(final SClass rcvrClass, final SSymbol selector,
      final DispatchGuard guard,
      final AbstractDispatchNode nextInCache) {
    super(nextInCache.getSourceSection());
    this.cachedMethod = Truffle.getRuntime().createDirectCallNode(
        getDnu(rcvrClass));
    this.selector = selector;
    this.guard    = guard;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    boolean match;
    Object rcvr = arguments[0];
    try {
      match = guard.entryMatches(rcvr);
    } catch (InvalidAssumptionException e) {
      match = false;
    }
    if (match) {
      return performDnu(frame, arguments, rcvr);
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }

  protected Object performDnu(final VirtualFrame frame, final Object[] arguments,
      final Object rcvr) {
    Object[] argsArr = new Object[] {
        rcvr, selector, SArguments.getArgumentsWithoutReceiver(arguments) };
    return cachedMethod.call(frame, argsArr);
  }

  @TruffleBoundary
  public static CallTarget getDnu(final SClass rcvrClass) {
    Dispatchable disp = rcvrClass.lookupMessage(
        Symbols.DNU, AccessModifier.PROTECTED);

    if (disp == null) {
      VM.errorExit("Lookup of " + rcvrClass.getName().getString() + ">>#doesNotUnderstand:arguments: failed.");
    }
    return ((SInvokable) disp).getCallTarget();
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }
}
