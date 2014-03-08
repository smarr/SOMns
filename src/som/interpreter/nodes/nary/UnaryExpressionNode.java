package som.interpreter.nodes.nary;

import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild(value = "receiver", type = ExpressionNode.class)
public abstract class UnaryExpressionNode extends ExpressionNode {

  public abstract Object executeEvaluated(final VirtualFrame frame,
      final Object receiver);
}
