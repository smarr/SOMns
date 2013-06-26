package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.vmobjects.Object;

public class ReturnNonLocalNode extends ContextualNode {

  @Child protected final ExpressionNode expression;
  
  public ReturnNonLocalNode(final ExpressionNode expression,
      final int contextLevel) {
    super(contextLevel);
    this.expression = adoptChild(expression);
  }
  
  @Override
  public Object executeGeneric(VirtualFrame frame) {
    throw new ReturnException(expression.executeGeneric(frame),
        (VirtualFrame)determineContext(frame));
  }
}
