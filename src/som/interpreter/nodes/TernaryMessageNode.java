package som.interpreter.nodes;

import som.vm.Universe;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChildren({
  @NodeChild(value = "receiver",  type = ExpressionNode.class),
  @NodeChild(value = "firstArg",  type = ExpressionNode.class),
  @NodeChild(value = "secondArg", type = ExpressionNode.class)})
public abstract class TernaryMessageNode extends AbstractMessageNode {
  public TernaryMessageNode(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  public TernaryMessageNode(final TernaryMessageNode node) {
    this(node.selector, node.universe);
  }

  public abstract Object executeEvaluated(final VirtualFrame frame, final Object receiver, Object firstArg, Object secondArg);
  public abstract ExpressionNode getFirstArg();
  public abstract ExpressionNode getSecondArg();
}
