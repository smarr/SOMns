package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;


public abstract class AbstractDispatchNode
    extends Node implements DispatchChain {
  public static final int INLINE_CACHE_SIZE = 6;

  public abstract Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments);
}
