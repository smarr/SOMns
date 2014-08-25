package som.interpreter.nodes.nary;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.PreevaluatedExpression;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;


@NodeChildren({
  @NodeChild(value = "receiver",  type = ExpressionNode.class),
  @NodeChild(value = "firstArg",  type = ExpressionNode.class),
  @NodeChild(value = "secondArg", type = ExpressionNode.class)})
public abstract class TernaryExpressionNode extends ExpressionNode
    implements PreevaluatedExpression {

  public TernaryExpressionNode(final SourceSection sourceSection,
      final boolean executesEnforced) {
    super(sourceSection, executesEnforced);
  }

  public TernaryExpressionNode(final boolean executesEnforced) {
    this(null, executesEnforced); }

  public abstract Object executeEvaluated(final VirtualFrame frame,
      final Object receiver, final Object firstArg, final Object secondArg);

  public abstract void executeEvaluatedVoid(final VirtualFrame frame,
      final Object receiver, final Object firstArg, final Object secondArg);

  @Override
  public final Object doPreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return executeEvaluated(frame, arguments[0], arguments[1], arguments[2]);
  }

  public abstract static class TernarySideEffectFreeExpressionNode
    extends TernaryExpressionNode {

    public TernarySideEffectFreeExpressionNode(final boolean executesEnforced) {
      super(executesEnforced);
    }

    @Override
    public final void executeVoid(final VirtualFrame frame) {
      /* NOOP, side effect free */
    }
  }
}
