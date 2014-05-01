package som.interpreter.nodes.nary;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.PreevaluatedExpression;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.frame.VirtualFrame;


@NodeChildren({
  @NodeChild(value = "receiver", type = ExpressionNode.class),
  @NodeChild(value = "argument", type = ExpressionNode.class)})
public abstract class BinaryExpressionNode extends ExpressionNode
    implements PreevaluatedExpression {

  public abstract Object executeEvaluated(final VirtualFrame frame,
      final Object receiver, Object argument);

  public abstract void executeEvaluatedVoid(final VirtualFrame frame,
      final Object receiver, Object argument);

  @Override
  public final Object executePreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return executeEvaluated(frame, arguments[0], arguments[1]);
  }

  public abstract static class BinarySideEffectFreeExpressionNode
    extends BinaryExpressionNode {

    @Override
    public final void executeVoid(final VirtualFrame frame) {
      /* NOOP, side effect free */
    }
  }
}
