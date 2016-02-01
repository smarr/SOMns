package som.interpreter.nodes.nary;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.PreevaluatedExpression;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.source.SourceSection;


@Instrumentable(factory = UnaryExpressionNodeWrapper.class)
@NodeChild(value = "receiver", type = ExpressionNode.class)
public abstract class UnaryExpressionNode extends ExpressionNode
    implements PreevaluatedExpression {

  public UnaryExpressionNode(final SourceSection source) {
    super(source);
  }

  // For nodes that are not representing source code
  public UnaryExpressionNode() { super(null); }

  public abstract Object executeEvaluated(final VirtualFrame frame,
      final Object receiver);

  @Override
  public final Object doPreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return executeEvaluated(frame, arguments[0]);
  }
}
