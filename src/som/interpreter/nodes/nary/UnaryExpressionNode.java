package som.interpreter.nodes.nary;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.PreevaluatedExpression;
import tools.dym.Tags.EagerlyWrapped;


@Instrumentable(factory = UnaryExpressionNodeWrapper.class)
@NodeChild(value = "receiver", type = ExpressionNode.class)
public abstract class UnaryExpressionNode extends ExpressionNode
    implements PreevaluatedExpression {

  private final boolean eagerlyWrapped;

  public UnaryExpressionNode(final boolean eagerlyWrapped,
      final SourceSection source) {
    super(source);
    this.eagerlyWrapped = eagerlyWrapped;
  }

  /**
   * For use by wrapper nodes only.
   */
  protected UnaryExpressionNode(final UnaryExpressionNode wrappedNode) {
    super(wrappedNode);
    assert wrappedNode.eagerlyWrapped : "I think this should be true.";
    this.eagerlyWrapped = false;
  }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == EagerlyWrapped.class) {
      return eagerlyWrapped;
    } else {
      return super.isTaggedWith(tag);
    }
  }

  public abstract Object executeEvaluated(final VirtualFrame frame,
      final Object receiver);

  @Override
  public final Object doPreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return executeEvaluated(frame, arguments[0]);
  }
}
