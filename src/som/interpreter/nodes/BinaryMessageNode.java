package som.interpreter.nodes;

import som.vm.Universe;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChildren({
  @NodeChild(value = "receiver", type = ExpressionNode.class),
  @NodeChild(value = "argument", type = ExpressionNode.class)})
public abstract class BinaryMessageNode extends AbstractMessageNode {

  public BinaryMessageNode(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  public BinaryMessageNode(final BinaryMessageNode node) {
    this(node.selector, node.universe);
  }

  public abstract Object executeEvaluated(final VirtualFrame frame, final Object receiver, Object argument);
  public abstract ExpressionNode getArgument();
}
