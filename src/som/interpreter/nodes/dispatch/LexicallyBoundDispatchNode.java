package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.DefaultCallTarget;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.instrumentation.InstrumentableDirectCallNode;
import som.interpreter.Method;
import som.interpreter.SArguments;
import som.vm.VmSettings;
import tools.asyncstacktraces.ShadowStackEntryLoad;
import tools.asyncstacktraces.ShadowStackEntryLoad.UninitializedShadowStackEntryLoad;


/**
 * Private methods are special, they are linked unconditionally to the call site.
 * Thus, we don't need to check at the dispatch whether they apply or not.
 */
public final class LexicallyBoundDispatchNode extends AbstractDispatchNode
    implements ShadowStackEntryMethodCacheCompatibleNode {

  @Child private DirectCallNode         cachedMethod;
  private final boolean                 requiresShadowStack;
  @CompilationFinal private boolean     uniqueCaller;
  @Child protected ShadowStackEntryLoad shadowStackEntryLoad =
      VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE ? new UninitializedShadowStackEntryLoad()
          : null;

  public LexicallyBoundDispatchNode(final SourceSection source,
      final CallTarget methodCallTarget) {
    super(source);
    cachedMethod = Truffle.getRuntime().createDirectCallNode(methodCallTarget);
    requiresShadowStack = ShadowStackEntryMethodCacheCompatibleNode.requiresShadowStack(
        (DefaultCallTarget) methodCallTarget, this);
    if (VmSettings.DYNAMIC_METRICS) {
      this.cachedMethod = insert(new InstrumentableDirectCallNode(cachedMethod, source));
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
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_METHOD_CACHE) {
      if (requiresShadowStack) {
        if (uniqueCaller) {
          assert frame.getArguments().length >= 2; // 1 for receiver 1 for SSentry
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
  }

  @Override
  public Method getCachedMethod() {
    RootCallTarget ct = (DefaultCallTarget) cachedMethod.getCallTarget();
    return (Method) ct.getRootNode();
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1;
  }
}
