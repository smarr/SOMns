package som.interpreter.nodes;

import som.vm.Universe;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class TernaryMessageNode extends AbstractNAryMessageNode {
  public TernaryMessageNode(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  public TernaryMessageNode(final TernaryMessageNode node) {
    this(node.selector, node.universe);
  }

  public abstract Object executeEvaluated(final VirtualFrame frame, final Object receiver, Object firstArg, Object secondArg);
}
