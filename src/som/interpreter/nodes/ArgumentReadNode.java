package som.interpreter.nodes;

import som.interpreter.Arguments;

import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class ArgumentReadNode extends ExpressionNode {
  protected final int argumentIndex;

  public ArgumentReadNode(final int argumentIndex) {
    this.argumentIndex = argumentIndex;
  }

  public static ArgumentReadNode create(final int argumentIndex, final int arity) {
    switch (arity) {
      case 1:  return new UnaryArgumentReadNode(argumentIndex);
      case 2:  return new BinaryArgumentReadNode(argumentIndex);
      case 3:  return new TernaryArgumentReadNode(argumentIndex);
      default: return new KeywordArgumentReadNode(argumentIndex);
    }
  }

  public static final class UnaryArgumentReadNode extends ArgumentReadNode {
    public UnaryArgumentReadNode(final int argumentIndex) { super(argumentIndex); }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return Arguments.getUnary(frame).getArgument(argumentIndex);
    }
  }

  public static final class BinaryArgumentReadNode extends ArgumentReadNode {
    public BinaryArgumentReadNode(final int argumentIndex) { super(argumentIndex); }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return Arguments.getBinary(frame).getArgument(argumentIndex);
    }
  }

  public static final class TernaryArgumentReadNode extends ArgumentReadNode {
    public TernaryArgumentReadNode(final int argumentIndex) { super(argumentIndex); }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return Arguments.getTernary(frame).getArgument(argumentIndex);
    }
  }

  public static final class KeywordArgumentReadNode extends ArgumentReadNode {
    public KeywordArgumentReadNode(final int argumentIndex) { super(argumentIndex); }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return Arguments.getKeyword(frame).getArgument(argumentIndex);
    }
  }
}
