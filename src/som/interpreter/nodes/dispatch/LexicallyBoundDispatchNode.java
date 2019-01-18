package som.interpreter.nodes.dispatch;

import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.DefaultCallTarget;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.instrumentation.CountingDirectCallNode;
import som.interpreter.Invokable;
import som.vm.VmSettings;
import tools.asyncstacktraces.ShadowStackEntryLoad;
import tools.asyncstacktraces.ShadowStackEntryLoad.UninitializedShadowStackEntryLoad;


/**
 * Private methods are special, they are linked unconditionally to the call site.
 * Thus, we don't need to check at the dispatch whether they apply or not.
 */
public final class LexicallyBoundDispatchNode extends AbstractDispatchNode
    implements BackCacheCallNode {

  @Child private DirectCallNode         cachedMethod;
  @CompilationFinal private boolean     uniqueCaller;
  @Child protected ShadowStackEntryLoad shadowStackEntryLoad =
      VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE ? new UninitializedShadowStackEntryLoad()
          : null;

  public LexicallyBoundDispatchNode(final SourceSection source,
      final CallTarget methodCallTarget) {
    super(source);
    cachedMethod = Truffle.getRuntime().createDirectCallNode(methodCallTarget);
    if (VmSettings.DYNAMIC_METRICS) {
      this.cachedMethod = new CountingDirectCallNode(this.cachedMethod);
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
    BackCacheCallNode.setShadowStackEntry(frame,
        uniqueCaller, arguments, this, shadowStackEntryLoad);
    return cachedMethod.call(arguments);
  }

  @Override
  public void collectDispatchStatistics(final Map<Invokable, Integer> result) {
    CountingDirectCallNode node = (CountingDirectCallNode) this.cachedMethod;
    result.put(node.getInvokable(), node.getCount());
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1;
  }
}
