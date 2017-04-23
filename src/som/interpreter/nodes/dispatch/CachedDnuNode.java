package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import som.VM;
import som.compiler.AccessModifier;
import som.interpreter.SArguments;
import som.interpreter.Types;
import som.primitives.SystemPrims.PrintStackTracePrim;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;


public final class CachedDnuNode extends AbstractDispatchNode {

  @Child private DirectCallNode       cachedMethod;
  @Child private AbstractDispatchNode nextInCache;

  private final DispatchGuard guard;
  private final SSymbol       selector;

  public CachedDnuNode(final SClass rcvrClass, final SSymbol selector,
      final DispatchGuard guard, final VM vm,
      final AbstractDispatchNode nextInCache) {
    super(nextInCache.getSourceSection());
    this.nextInCache  = nextInCache;
    this.cachedMethod = Truffle.getRuntime().createDirectCallNode(
        getDnu(rcvrClass, selector, vm));
    this.selector = selector;
    this.guard    = guard;
  }

  @Override
  public Object executeDispatch(final Object[] arguments) {
    boolean match;
    Object rcvr = arguments[0];
    try {
      match = guard.entryMatches(rcvr);
    } catch (InvalidAssumptionException e) {
      match = false;
    }
    if (match) {
      return performDnu(arguments, rcvr);
    } else {
      return nextInCache.executeDispatch(arguments);
    }
  }

  protected Object performDnu(final Object[] arguments,
      final Object rcvr) {
    if (VmSettings.DNU_PRINT_STACK_TRACE) {
      PrintStackTracePrim.printStackTrace(0, getSourceSection());
      VM.errorPrintln("Lookup of " + selector + " failed in " + Types.getClassOf(rcvr).getName().getString());
    }

    Object[] argsArr = new Object[] {
        rcvr, selector, SArguments.getArgumentsWithoutReceiver(arguments) };
    return cachedMethod.call(argsArr);
  }

  @TruffleBoundary
  public static CallTarget getDnu(final SClass rcvrClass,
      final SSymbol missingSymbol, final VM vm) {
    Dispatchable disp = rcvrClass.lookupMessage(
        Symbols.DNU, AccessModifier.PROTECTED);

    if (disp == null) {
      vm.errorExit("Lookup of " + rcvrClass.getName().getString() + ">>#doesNotUnderstand:arguments: failed after failed lookup for: " + missingSymbol.toString());
    }
    return ((SInvokable) disp).getCallTarget();
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }
}
