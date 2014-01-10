package som.interpreter.nodes;

import som.compiler.Variable.Argument;
import som.interpreter.Arguments;

import com.oracle.truffle.api.frame.VirtualFrame;

public class ArgumentReadNode extends ExpressionNode {
  private final int argumentIndex;
  public ArgumentReadNode(final Argument arg) {
    this.argumentIndex = arg.index;
  }

  public ArgumentReadNode(final int argumentIndex) {
    this.argumentIndex = argumentIndex;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return Arguments.get(frame).getArgument(argumentIndex);
  }
}
