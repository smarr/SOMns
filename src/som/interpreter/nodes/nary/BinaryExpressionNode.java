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


@NodeChildren({
  @NodeChild(value = "receiver", type = ExpressionNode.class),
  @NodeChild(value = "argument", type = ExpressionNode.class)})
@Instrumentable(factory = BinaryExpressionNodeWrapper.class)
public abstract class BinaryExpressionNode extends ExprWithTagsNode
    implements PreevaluatedExpression {
  @CompilationFinal private boolean eagerlyWrapped;

  public BinaryExpressionNode(final boolean eagerlyWrapped,
      final SourceSection source) {
    super(source);
    this.eagerlyWrapped = eagerlyWrapped;
  }

  /**
   * For wrapped nodes only.
   */
  protected BinaryExpressionNode(final BinaryExpressionNode wrappedNode) {
    super(wrappedNode);
    assert !wrappedNode.eagerlyWrapped : "I think this should be true.";
    this.eagerlyWrapped = false;
  }

  /**
   * This method is used by eager wrapper or if this node is not eagerly
   * wrapped.
   */
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    return super.isTaggedWith(tag);
  }

  @Override
  protected final boolean isTaggedWith(final Class<?> tag) {
    if (eagerlyWrapped) {
      return false;
    } else {
      return isTaggedWithIgnoringEagerness(tag);
    }
  }

  @Override
  protected void onReplace(final Node newNode, final CharSequence reason) {
    if (newNode instanceof WrapperNode ||
        !(newNode instanceof BinaryExpressionNode)) { return; }

    BinaryExpressionNode n = (BinaryExpressionNode) newNode;
    n.eagerlyWrapped = eagerlyWrapped;
    super.onReplace(newNode, reason);
  }

  public abstract Object executeEvaluated(final VirtualFrame frame,
      final Object receiver, Object argument);

  @Override
  public final Object doPreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return executeEvaluated(frame, arguments[0], arguments[1]);
  }
}
