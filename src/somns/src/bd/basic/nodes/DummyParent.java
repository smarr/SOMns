package bd.basic.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


/**
 * Dummy Node to work around Truffle's restriction that a node, which is going to
 * be instrumented, needs to have a parent.
 */
public final class DummyParent extends RootNode {
  @Child public Node child;

  public DummyParent(final TruffleLanguage<?> language, final Node node) {
    super(language);
    this.child = insert(node);
  }

  public void notifyInserted() {
    super.notifyInserted(child);
  }

  @Override
  public Object execute(final VirtualFrame frame) {
    CompilerDirectives.transferToInterpreter();
    throw new UnsupportedOperationException(
        "This is a dummy node, and should not be executed");
  }
}
