package som.interpreter.nodes.nary;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.PreevaluatedExpression;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

@NodeChild(value = "receiver", type = ExpressionNode.class)
public abstract class UnaryExpressionNode extends ExpressionNode
    implements PreevaluatedExpression {

  public UnaryExpressionNode(final SourceSection source,
      final boolean executesEnforced) {
    super(source, executesEnforced);
  }

  public abstract Object executeEvaluated(final VirtualFrame frame,
      final Object receiver);

  public abstract void executeEvaluatedVoid(final VirtualFrame frame,
      final Object receiver);

  @Override
  public final Object doPreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return executeEvaluated(frame,
        CompilerDirectives.unsafeCast(arguments[0], Object.class, true, true));
  }

  public abstract static class UnarySideEffectFreeExpressionNode
      extends UnaryExpressionNode {

    public UnarySideEffectFreeExpressionNode(final boolean executesEnforced) {
      super(null, executesEnforced);
    }

    @Override
    public final void executeVoid(final VirtualFrame frame) {
      /* NOOP, side effect free */
    }
  }
}
