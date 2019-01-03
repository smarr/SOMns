package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.DefaultCallTarget;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import som.instrumentation.InstrumentableDirectCallNode;
import som.interpreter.Method;
import som.interpreter.SArguments;
import som.interpreter.actors.EventualMessage;
import som.vm.VmSettings;
import tools.asyncstacktraces.ShadowStackEntryLoad;
import tools.asyncstacktraces.ShadowStackEntryLoad.UninitializedShadowStackEntryLoad;


public final class CachedDispatchNode extends AbstractDispatchNode
    implements ShadowStackEntryMethodCacheCompatibleNode {

  @Child private DirectCallNode         cachedMethod;
  @Child private AbstractDispatchNode   nextInCache;
  private final boolean                 requiresShadowStack;
  @CompilationFinal private boolean     uniqueCaller;
  @Child protected ShadowStackEntryLoad shadowStackEntryLoad =
      VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE ? new UninitializedShadowStackEntryLoad()
          : null;

  private final DispatchGuard guard;

  public CachedDispatchNode(final CallTarget methodCallTarget,
      final DispatchGuard guard, final AbstractDispatchNode nextInCache) {
    super(nextInCache.getSourceSection());
    this.guard = guard;
    this.nextInCache = nextInCache;
    this.cachedMethod = Truffle.getRuntime().createDirectCallNode(methodCallTarget);
    requiresShadowStack = ShadowStackEntryMethodCacheCompatibleNode.requiresShadowStack(
        (DefaultCallTarget) methodCallTarget, this);
    if (VmSettings.DYNAMIC_METRICS) {
      this.cachedMethod = insert(new InstrumentableDirectCallNode(cachedMethod,
          nextInCache.getSourceSection()));
    }
  }

  @Override
  public void uniqueCaller() {
    uniqueCaller = true;
  }

  @Override
  public void multipleCaller() {
    uniqueCaller = false;
  }

  @Override
  public Method getCachedMethod() {
    RootCallTarget ct = (DefaultCallTarget) cachedMethod.getCallTarget();
    return (Method) ct.getRootNode();
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    try {
      if (guard.entryMatches(arguments[0])) {
        if (VmSettings.ACTOR_ASYNC_STACK_TRACE_METHOD_CACHE) {
          if (requiresShadowStack) {
            if (uniqueCaller) {
              // At least two entries, receiver and SSEntry,
              // Except from VM main (start) which we heuristically assert against start string
              assert frame.getArguments().length >= 2 ||
                  ((frame.getArguments()[0] instanceof EventualMessage.DirectMessage)
                      && (((EventualMessage.DirectMessage) frame.getArguments()[0])).getSelector()
                                                                                    .getString()
                                                                                    .equals(
                                                                                        "start"));
              SArguments.setShadowStackEntry(arguments, SArguments.getShadowStackEntry(frame));
            } else {
              SArguments.setShadowStackEntryWithCache(arguments, this,
                  shadowStackEntryLoad, frame, false);
            }
          }
        } else if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
          SArguments.setShadowStackEntryWithCache(arguments, this,
              shadowStackEntryLoad, frame, false);
        }
        return cachedMethod.call(arguments);
      } else {
        return nextInCache.executeDispatch(frame, arguments);
      }
    } catch (InvalidAssumptionException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return replace(nextInCache).executeDispatch(frame, arguments);
    }
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }
}
