package som.interpreter.nodes;

import som.interpreter.SArguments;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class ArgumentReadNode extends ExpressionNode
    implements PreevaluatedExpression {
  protected final int argumentIndex;

  public ArgumentReadNode(final int argumentIndex) {
    this.argumentIndex = argumentIndex;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return SArguments.getArgumentsFromFrame(frame)[argumentIndex];
  }

  @Override
  public Object executePreEvaluated(final VirtualFrame frame,
      final Object receiver, final Object[] arguments) {
    return arguments[argumentIndex];
  }

  public static final class SelfArgumentReadNode extends ExpressionNode
      implements PreevaluatedExpression {

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return SArguments.getReceiverFromFrame(frame);
    }

    @Override
    public Object executePreEvaluated(final VirtualFrame frame,
        final Object receiver, final Object[] arguments) {
      return receiver;
    }

    @Override
    public void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
  }

  @Override
  public void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
}
