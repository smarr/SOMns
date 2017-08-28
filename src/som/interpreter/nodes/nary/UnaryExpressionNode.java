package som.interpreter.nodes.nary;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;

import bd.nodes.WithContext;
import som.VM;
import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;


@Instrumentable(factory = UnaryExpressionNodeWrapper.class)
@NodeChild(value = "receiver", type = ExpressionNode.class)
public abstract class UnaryExpressionNode extends EagerlySpecializableNode {

  protected UnaryExpressionNode() {}

  protected UnaryExpressionNode(final UnaryExpressionNode wrappedNode) {}

  public abstract Object executeEvaluated(VirtualFrame frame, Object receiver);

  @Override
  public final Object doPreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return executeEvaluated(frame, arguments[0]);
  }

  @Override
  public EagerPrimitiveNode wrapInEagerWrapper(final SSymbol selector,
      final ExpressionNode[] arguments, final VM vm) {
    EagerUnaryPrimitiveNode result = new EagerUnaryPrimitiveNode(selector, arguments[0], this);
    result.initialize(sourceSection);
    return result;
  }

  public abstract static class UnarySystemOperation extends UnaryExpressionNode
      implements WithContext<UnarySystemOperation, VM> {
    @CompilationFinal protected VM vm;

    @Override
    public UnarySystemOperation initialize(final VM vm) {
      assert this.vm == null && vm != null;
      this.vm = vm;
      return this;
    }
  }
}
