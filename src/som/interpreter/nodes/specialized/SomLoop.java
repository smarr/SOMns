package som.interpreter.nodes.specialized;

import som.interpreter.Invokable;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


public abstract class SomLoop {

  public static void reportLoopCount(final long count, final Node loopNode) {
    if (count < 1) { return; }

    CompilerAsserts.neverPartOfCompilation("reportLoopCount");
    Node current = loopNode.getParent();
    while (current != null && !(current instanceof RootNode)) {
      current = current.getParent();
    }
    if (current != null) {
      ((Invokable) current).propagateLoopCountThroughoutMethodScope(count);
    }
  }
}
