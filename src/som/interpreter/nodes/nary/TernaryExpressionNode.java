package som.interpreter.nodes.nary;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.PreevaluatedExpression;

import com.oracle.truffle.api.CompilerDirectives;
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

  public TernaryExpressionNode(final SourceSection sourceSection) {
    super(sourceSection);
  }

  public TernaryExpressionNode() { this(null); }

  public abstract Object executeEvaluated(final VirtualFrame frame,
      final Object receiver, final Object firstArg, final Object secondArg);

  public abstract void executeEvaluatedVoid(final VirtualFrame frame,
      final Object receiver, final Object firstArg, final Object secondArg);

  @Override
  public final Object doPreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return executeEvaluated(frame,
        CompilerDirectives.unsafeCast(arguments[0], Object.class, true, true),
        CompilerDirectives.unsafeCast(arguments[1], Object.class, true, true),
        CompilerDirectives.unsafeCast(arguments[2], Object.class, true, true));
  }

  public abstract static class TernarySideEffectFreeExpressionNode
    extends TernaryExpressionNode {

    @Override
    public final void executeVoid(final VirtualFrame frame) {
      /* NOOP, side effect free */
    }
  }
}
