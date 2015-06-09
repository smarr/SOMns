package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;


/**
 * Private methods are special, they are linked unconditionally to the call site.
 * Thus, we don't need to check at the dispatch whether they apply or not.
 */
public final class PrivateStaticBoundDispatchNode extends AbstractDispatchNode {

  @Child private DirectCallNode cachedMethod;

  public PrivateStaticBoundDispatchNode(final CallTarget methodCallTarget) {
    cachedMethod = Truffle.getRuntime().createDirectCallNode(methodCallTarget);
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    return cachedMethod.call(frame, arguments);
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1;
  }
}
