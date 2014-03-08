package som.interpreter.nodes.nary;

import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.frame.VirtualFrame;


@NodeChildren({
  @NodeChild(value = "receiver", type = ExpressionNode.class),
  @NodeChild(value = "argument", type = ExpressionNode.class)})
public abstract class BinaryExpressionNode extends ExpressionNode {

  public abstract Object executeEvaluated(final VirtualFrame frame,
      final Object receiver, Object argument);
}
