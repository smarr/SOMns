package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.ExprWithTagsNode;


@NodeInfo(cost = NodeCost.NONE)
public final class InternalObjectArrayNode extends ExprWithTagsNode {
  @Children private final ExpressionNode[] expressions;

  public InternalObjectArrayNode(final ExpressionNode[] expressions,
      final SourceSection source) {
    super(source);
    this.expressions = expressions;
  }

  @Override
  @ExplodeLoop
  public Object[] executeObjectArray(final VirtualFrame frame) {
    Object[] values = new Object[expressions.length];
    for (int i = 0; i < expressions.length; i++) {
      values[i] = expressions[i].executeGeneric(frame);
    }
    return values;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return executeObjectArray(frame);
  }
}
