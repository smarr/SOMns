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

  @ExplodeLoop
  public boolean checkGuards(final Object[] arguments) throws InvalidAssumptionException {
    for (int i = 0; i < guards.length; i++) {
      DispatchGuard guard = guards[i];
      Object arg = arguments[i];
      boolean matches = guard.entryMatches(arg);
      if (!matches) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Object executeDispatch(final Object[] arguments) {
    try {
      if (checkGuards(arguments)) {
        return cachedMethod.call(arguments);
      } else {
        return nextInCache.executeDispatch(arguments);
      }
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
