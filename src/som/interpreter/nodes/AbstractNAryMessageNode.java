package som.interpreter.nodes;

import som.vm.Universe;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;

@NodeChildren({
  @NodeChild(value = "receiver", type = ExpressionNode.class),
  @NodeChild(value = "arguments", type = ExpressionNode[].class)})
public abstract class AbstractNAryMessageNode extends AbstractMessageNode {

  public AbstractNAryMessageNode(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  public AbstractNAryMessageNode(final AbstractNAryMessageNode node) {
    this(node.selector, node.universe);
  }

  public abstract ExpressionNode[] getArguments();
}
