package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;


public abstract class AbstractDispatchNode extends Node {
  protected static final int INLINE_CACHE_SIZE = 6;

  public abstract Object executeDispatch(VirtualFrame frame,
      SArguments arguments);
}
