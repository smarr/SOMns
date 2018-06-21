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


public final class CachedDispatchNode extends AbstractDispatchNode {

  @Child private DirectCallNode       cachedMethod;
  @Child private AbstractDispatchNode nextInCache;

  @CompilationFinal private final DispatchGuard guard;

  @Children private final TypeCheckNode[] typeChecks;

  public CachedDispatchNode(final CallTarget methodCallTarget,
      final DispatchGuard guard, final TypeCheckNode[] types,
      final AbstractDispatchNode nextInCache) {
    super(nextInCache.getSourceSection());
    this.guard = guard;
    this.typeChecks = types;
    this.nextInCache = nextInCache;
    this.cachedMethod = Truffle.getRuntime().createDirectCallNode(methodCallTarget);
    if (VmSettings.DYNAMIC_METRICS) {
      this.cachedMethod = insert(new InstrumentableDirectCallNode(cachedMethod,
          nextInCache.getSourceSection()));
      VM.insertInstrumentationWrapper(cachedMethod);
    }
  }

  @Override
  @ExplodeLoop
  public Object executeDispatch(final Object[] arguments) {
    performTypeChecks(arguments);

    Object ret;
    try {
      if (guard.entryMatches(arguments[0], sourceSection)) {
        ret = cachedMethod.call(arguments);
      } else {
        ret = nextInCache.executeDispatch(arguments);
      }

      performReturnValueTypeCheck(ret);
      return ret;
    } catch (InvalidAssumptionException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return replace(nextInCache).executeDispatch(arguments);
    }
  }

  private void performReturnValueTypeCheck(final Object ret) {
    if (!VmSettings.USE_TYPE_CHECKING) {
      return;
    }

    TypeCheckNode node = typeChecks[typeChecks.length - 1];
    if (node != null) {
      node.executeTypeCheck(ret);
    }
  }

  private void performTypeChecks(final Object[] arguments) {
    if (!VmSettings.USE_TYPE_CHECKING) {
      return;
    }

    for (int i = 0; i < typeChecks.length - 1; i++) { // not the return type
      TypeCheckNode node = typeChecks[i];
      if (node != null) {
        node.executeTypeCheck(arguments[i + 1]);
      }
    }
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }
}
