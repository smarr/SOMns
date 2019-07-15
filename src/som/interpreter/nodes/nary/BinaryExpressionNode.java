package som.interpreter.nodes.nary;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;

import som.VM;
import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;


@NodeChildren({
    @NodeChild(value = "receiver", type = ExpressionNode.class),
    @NodeChild(value = "argument", type = ExpressionNode.class)})
@GenerateWrapper
public abstract class BinaryExpressionNode extends EagerlySpecializableNode {

  protected BinaryExpressionNode() {}

  protected BinaryExpressionNode(final BinaryExpressionNode wrappedNode) {}

  public abstract Object executeEvaluated(VirtualFrame frame, Object receiver,
      Object argument);

  @Override
  public WrapperNode createWrapper(final ProbeNode probe) {
    return new BinaryExpressionNodeWrapper(this, probe);
  }

  @Override
  public final Object doPreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return executeEvaluated(frame, arguments[0], arguments[1]);
  }

  @Override
  public EagerPrimitiveNode wrapInEagerWrapper(final SSymbol selector,
      final ExpressionNode[] arguments, final VM vm) {
    EagerBinaryPrimitiveNode result =
        new EagerBinaryPrimitiveNode(selector, arguments[0], arguments[1], this);
    result.initialize(sourceSection);
    return result;
  }
}
