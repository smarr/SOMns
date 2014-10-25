package som.interpreter.nodes;

import som.interpreter.SArguments;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class ArgumentReadNode extends ExpressionNode
    implements PreevaluatedExpression {
  protected final int argumentIndex;

  public ArgumentReadNode(final int argumentIndex) {
    super(null);
    assert argumentIndex >= 0;
    this.argumentIndex = argumentIndex;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return SArguments.arg(frame, argumentIndex);
  }

  @Override
  public Object doPreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return arguments[argumentIndex];
  }
}
