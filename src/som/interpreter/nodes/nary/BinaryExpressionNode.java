package som.interpreter.nodes.nary;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;


@NodeChildren({
  @NodeChild(value = "receiver", type = ExpressionNode.class),
  @NodeChild(value = "argument", type = ExpressionNode.class)})
@Instrumentable(factory = BinaryExpressionNodeWrapper.class)
public abstract class BinaryExpressionNode extends EagerlySpecializableNode {

  public BinaryExpressionNode(final boolean eagerlyWrapped,
      final SourceSection source) {
    super(eagerlyWrapped, source);
  }

  /**
   * For wrapped nodes only.
   */
  protected BinaryExpressionNode(final BinaryExpressionNode wrappedNode) {
    super(wrappedNode);
  }

  public abstract Object executeEvaluated(VirtualFrame frame, Object receiver,
      Object argument);

  @Override
  public final Object doPreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return executeEvaluated(frame, arguments[0], arguments[1]);
  }

  @Override
  public EagerPrimitive wrapInEagerWrapper(
      final EagerlySpecializableNode prim, final SSymbol selector,
      final ExpressionNode[] arguments) {
    return new EagerBinaryPrimitiveNode(getSourceSection(), selector,
        arguments[0], arguments[1], this);
  }
}
