package som.interpreter.nodes;

import som.compiler.Variable.Argument;
import som.vmobjects.SAbstractObject;

import com.oracle.truffle.api.frame.VirtualFrame;

public class ArgumentReadNode extends ContextualNode {
  private final int argumentIndex;
  public ArgumentReadNode(final Argument arg, final int contextLevel) {
    super(contextLevel);
    this.argumentIndex = arg.index;
  }

  public ArgumentReadNode(final int contextLevel, final int argumentIndex) {
    super(contextLevel);
    this.argumentIndex = argumentIndex;
  }

  @Override
  public SAbstractObject executeGeneric(final VirtualFrame frame) {
    return determineOuterArguments(frame).getArgument(argumentIndex);
  }
}
