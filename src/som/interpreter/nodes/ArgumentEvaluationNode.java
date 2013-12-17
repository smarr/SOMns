package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;


public class ArgumentEvaluationNode extends ExpressionNode {

  @Children private final ExpressionNode[] arguments;

  public ArgumentEvaluationNode(final ExpressionNode[] arguments) {
    this.arguments = adoptChildren(arguments);
  }

  public ArgumentEvaluationNode() {
    this.arguments = null;
  }

  public ExpressionNode getArgument(final int idx) {
    return arguments[idx];
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return executeArray(frame);
  }

  @Override
  @ExplodeLoop
  public Object[] executeArray(final VirtualFrame frame) {
    if (arguments == null || arguments.length == 0) {
      return null;
    }

    Object[] result = new Object[arguments.length];

    for (int i = 0; i < arguments.length; i++) {
      result[i] = arguments[i].executeGeneric(frame);
    }

    return result;
  }

}
