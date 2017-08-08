package som.interpreter.nodes.nary;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.frame.VirtualFrame;

import som.interpreter.nodes.ExpressionNode;
import som.vm.NotYetImplementedException;
import som.vmobjects.SSymbol;


@NodeChildren({
    @NodeChild(value = "receiver", type = ExpressionNode.class),
    @NodeChild(value = "firstArg", type = ExpressionNode.class),
    @NodeChild(value = "secondArg", type = ExpressionNode.class),
    @NodeChild(value = "thirdArg", type = ExpressionNode.class)})
public abstract class QuaternaryExpressionNode extends EagerlySpecializableNode {

  public abstract Object executeEvaluated(VirtualFrame frame, Object receiver,
      Object firstArg, Object secondArg, Object thirdArg);

  @Override
  public final Object doPreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return executeEvaluated(frame, arguments[0], arguments[1], arguments[2],
        arguments[3]);
  }

  @Override
  public EagerPrimitive wrapInEagerWrapper(final SSymbol selector,
      final ExpressionNode[] arguments) {
    throw new NotYetImplementedException(); // wasn't needed so far
  }
}
