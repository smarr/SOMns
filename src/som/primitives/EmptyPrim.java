package som.primitives;

import som.interpreter.nodes.AbstractMessageNode;
import som.interpreter.nodes.ExpressionNode;
import som.vm.Universe;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;

public class EmptyPrim extends AbstractMessageNode {
  private EmptyPrim(final SSymbol selector, final Universe universe,
      final ExpressionNode receiver) {
    super(selector, universe);
    this.receiver = adoptChild(receiver);
  }

  @Child private ExpressionNode receiver;

  @Override
  public ExpressionNode getReceiver() {
    return receiver;
  }

  public EmptyPrim(final EmptyPrim node) {
    this(node.selector, node.universe, node.receiver);
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    Universe.println("Warning: undefined primitive "
        + this.selector.getString() + " called");
    return null;
  }

  public static EmptyPrim create(final SSymbol selector, final Universe universe,
      final ExpressionNode receiver) {
    return new EmptyPrim(selector, universe, receiver);
  }
}
