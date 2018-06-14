package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import som.VM;
import som.instrumentation.InstrumentableDirectCallNode;
import som.vm.VmSettings;
import som.vm.constants.KernelObj;
import som.vm.constants.Nil;


public final class CachedDispatchNode extends AbstractDispatchNode {

  @Child private DirectCallNode       cachedMethod;
  @Child private AbstractDispatchNode nextInCache;

  @CompilationFinal(dimensions = 1) private final DispatchGuard[] guards;

  public CachedDispatchNode(final CallTarget methodCallTarget,
      final DispatchGuard[] guards, final AbstractDispatchNode nextInCache) {
    super(nextInCache.getSourceSection());
    this.guards = guards;
    this.nextInCache = nextInCache;
    this.cachedMethod = Truffle.getRuntime().createDirectCallNode(methodCallTarget);
    if (VmSettings.DYNAMIC_METRICS) {
      this.cachedMethod = insert(new InstrumentableDirectCallNode(cachedMethod,
          nextInCache.getSourceSection()));
      VM.insertInstrumentationWrapper(cachedMethod);
    }
  }

  public boolean checkReturnValue(final Object ret) throws InvalidAssumptionException {

    // skip when the only guard is for self
    if (guards.length == 1) {
      return true;
    }

    DispatchGuard guard = guards[guards.length - 1];
    boolean matches = ret == Nil.nilObject || guard.entryMatches(ret, sourceSection);
    if (!matches) {
      return false;
    }
    return true;
  }

  @ExplodeLoop
  public boolean checkGuards(final Object[] arguments) throws InvalidAssumptionException {
    for (int i = 0; i < guards.length - 1; i++) { // skip the return check
      DispatchGuard guard = guards[i];
      Object arg = arguments[i];
      boolean matches =
          arg == Nil.nilObject || guard.entryMatches(arg, sourceSection);
      if (!matches) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Object executeDispatch(final Object[] arguments) {
    Object ret;
    try {
      if (checkGuards(arguments)) {
        ret = cachedMethod.call(arguments);
      } else {
        ret = nextInCache.executeDispatch(arguments);
      }

      checkReturnValue(ret);
      return ret;
    } catch (InvalidAssumptionException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return replace(nextInCache).executeDispatch(arguments);
    } catch (IllegalArgumentException e) {
      KernelObj.signalException("signalArgumentError:", e.getMessage());
      return null;
    }
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }
}
