package som.interpreter.nodes;

import som.vm.Universe;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class BinaryMessageNode extends AbstractNAryMessageNode {

  public BinaryMessageNode(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  public BinaryMessageNode(final BinaryMessageNode node) {
    this(node.selector, node.universe);
  }

  public abstract Object executeEvaluated(final VirtualFrame frame, final Object receiver, Object argument);
}
