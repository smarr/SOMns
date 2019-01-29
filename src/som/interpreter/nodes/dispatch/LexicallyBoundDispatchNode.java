package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.DefaultCallTarget;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.instrumentation.InstrumentableDirectCallNode;
import som.interpreter.Method;
import som.vm.VmSettings;
import tools.asyncstacktraces.ShadowStackEntryLoad;
import tools.asyncstacktraces.ShadowStackEntryLoad.UninitializedShadowStackEntryLoad;


/**
 * Private methods are special, they are linked unconditionally to the call site.
 * Thus, we don't need to check at the dispatch whether they apply or not.
 */
public abstract class LexicallyBoundDispatchNode extends AbstractDispatchNode
    implements BackCacheCallNode {

  private final Assumption              stillUniqueCaller;
  @Child private DirectCallNode         cachedMethod;
  @CompilationFinal private boolean     uniqueCaller;
  @Child protected ShadowStackEntryLoad shadowStackEntryLoad =
      VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE ? new UninitializedShadowStackEntryLoad()
          : null;

  public LexicallyBoundDispatchNode(final SourceSection source,
      final CallTarget methodCallTarget) {
    super(source);
    stillUniqueCaller = Truffle.getRuntime().createAssumption();
    cachedMethod = Truffle.getRuntime().createDirectCallNode(methodCallTarget);
    if (VmSettings.DYNAMIC_METRICS) {
      this.cachedMethod = insert(new InstrumentableDirectCallNode(cachedMethod, source));
    }
    BackCacheCallNode.initializeUniqueCaller((RootCallTarget) methodCallTarget, this);
  }

  @Override
  public void makeUniqueCaller() {
    uniqueCaller = true;
  }

  @Override
  public void makeMultipleCaller() {
    uniqueCaller = false;
    stillUniqueCaller.invalidate();
  }

  @Override
  public Method getCachedMethod() {
    RootCallTarget ct = (DefaultCallTarget) cachedMethod.getCallTarget();
    return (Method) ct.getRootNode();
  }

  @Override
  public abstract Object executeDispatch(VirtualFrame frame, Object[] arguments);

  @Specialization(assumptions = "stillUniqueCaller", guards = "uniqueCaller")
  public Object uniqueCallerDispatch(final VirtualFrame frame, final Object[] arguments) {
    BackCacheCallNode.setShadowStackEntry(frame,
        true, arguments, this, shadowStackEntryLoad);
    return cachedMethod.call(arguments);
  }

  @Specialization(guards = "!uniqueCaller")
  public Object multipleCallerDispatch(final VirtualFrame frame, final Object[] arguments) {
    BackCacheCallNode.setShadowStackEntry(frame,
        false, arguments, this, shadowStackEntryLoad);
    return cachedMethod.call(arguments);
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1;
  }
}
