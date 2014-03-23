package som.interpreter.nodes.nary;

import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.frame.VirtualFrame;


@NodeChildren({
  @NodeChild(value = "receiver",  type = ExpressionNode.class),
  @NodeChild(value = "firstArg",  type = ExpressionNode.class),
  @NodeChild(value = "secondArg", type = ExpressionNode.class),
  @NodeChild(value = "thirdArg",  type = ExpressionNode.class)})
public abstract class QuaternaryExpressionNode extends ExpressionNode {

  public QuaternaryExpressionNode(final SourceSection sourceSection) {
    super(sourceSection);
  }

  public QuaternaryExpressionNode() { this(null); }

  public abstract Object executeEvaluated(final VirtualFrame frame,
      final Object receiver, final Object firstArg, final Object secondArg,
      final Object thirdArg);

  public abstract void executeEvaluatedVoid(final VirtualFrame frame,
      final Object receiver, final Object firstArg, final Object secondArg,
      final Object thirdArg);

  public abstract static class QuaternarySideEffectFreeExpressionNode
    extends QuaternaryExpressionNode {

    @Override
    public final void executeVoid(final VirtualFrame frame) {
      /* NOOP, side effect free */
    }
  }
}
