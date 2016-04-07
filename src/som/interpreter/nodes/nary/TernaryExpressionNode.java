package som.interpreter.nodes.nary;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.PreevaluatedExpression;
import tools.dym.Tags.EagerlyWrapped;


@NodeChildren({
  @NodeChild(value = "receiver",  type = ExpressionNode.class),
  @NodeChild(value = "firstArg",  type = ExpressionNode.class),
  @NodeChild(value = "secondArg", type = ExpressionNode.class)})
@Instrumentable(factory = TernaryExpressionNodeWrapper.class)
public abstract class TernaryExpressionNode extends ExpressionNode
    implements PreevaluatedExpression {

  @CompilationFinal private boolean eagerlyWrapped;

  public TernaryExpressionNode(final boolean eagerlyWrapped,
      final SourceSection sourceSection) {
    super(sourceSection);
    this.eagerlyWrapped = eagerlyWrapped;
  }

  /**
   * For wrapper nodes only.
   */
  protected TernaryExpressionNode(final TernaryExpressionNode wrappedNode) {
    super(wrappedNode);
    assert !wrappedNode.eagerlyWrapped : "I think this should be true.";
    this.eagerlyWrapped = false;
  }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == EagerlyWrapped.class) {
      return eagerlyWrapped;
    } else if (eagerlyWrapped) {
      // an eagerly wrapped node itself should not handle anything,
      // should be covered by the eager wrapper, I think
      return false;
    } else {
      return super.isTaggedWith(tag);
    }
  }

  @Override
  protected void onReplace(final Node newNode, final CharSequence reason) {
    if (newNode instanceof WrapperNode) { return; }
    TernaryExpressionNode n = (TernaryExpressionNode) newNode;
    n.eagerlyWrapped = eagerlyWrapped;
    super.onReplace(newNode, reason);
  }

  public abstract Object executeEvaluated(final VirtualFrame frame,
      final Object receiver, final Object firstArg, final Object secondArg);

  @Override
  public final Object doPreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return executeEvaluated(frame, arguments[0], arguments[1], arguments[2]);
  }
}
