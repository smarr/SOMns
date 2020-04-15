package som.interpreter.nodes.dispatch;

import java.util.HashMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.instrumentation.CountingDirectCallNode;
import som.interpreter.Invokable;
import som.vm.VmSettings;


/**
 * Private methods are special, they are linked unconditionally to the call site.
 * Thus, we don't need to check at the dispatch whether they apply or not.
 */
public final class LexicallyBoundDispatchNode extends AbstractDispatchNode {

  @Child private DirectCallNode cachedMethod;

  public LexicallyBoundDispatchNode(final SourceSection source,
      final CallTarget methodCallTarget) {
    super(source);
    cachedMethod = Truffle.getRuntime().createDirectCallNode(methodCallTarget);
    if (VmSettings.DYNAMIC_METRICS) {
      this.cachedMethod = new CountingDirectCallNode(this.cachedMethod);
    }
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    return cachedMethod.call(arguments);
  }

  @Override
  public void collectDispatchStatistics(final HashMap<Invokable, Integer> result) {
    CountingDirectCallNode node = (CountingDirectCallNode) this.cachedMethod;
    result.put(node.getInvokable(), node.getCount());
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1;
  }
}
