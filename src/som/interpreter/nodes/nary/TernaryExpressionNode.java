package som.interpreter.nodes.nary;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.PreevaluatedExpression;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.source.SourceSection;


@NodeChildren({
  @NodeChild(value = "receiver",  type = ExpressionNode.class),
  @NodeChild(value = "firstArg",  type = ExpressionNode.class),
  @NodeChild(value = "secondArg", type = ExpressionNode.class)})
@Instrumentable(factory = TernaryExpressionNodeWrapper.class)
public abstract class TernaryExpressionNode extends ExpressionNode
    implements PreevaluatedExpression {

  public TernaryExpressionNode(final SourceSection sourceSection) {
    super(sourceSection);
  }

  /**
   * For wrapper nodes only.
   */
  protected TernaryExpressionNode(final TernaryExpressionNode wrappedNode) {
    super(wrappedNode);
  }

  public TernaryExpressionNode() { this((SourceSection) null); }

  public abstract Object executeEvaluated(final VirtualFrame frame,
      final Object receiver, final Object firstArg, final Object secondArg);

  @Override
  public final Object doPreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return executeEvaluated(frame, arguments[0], arguments[1], arguments[2]);
  }
}
