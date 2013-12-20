package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class NonLocalVariableNode extends ContextualNode {

  protected final int upvalueIndex;

  private NonLocalVariableNode(final int contextLevel, final int upvalueIndex) {
    super(contextLevel);
    this.upvalueIndex = upvalueIndex;
  }

  public static class NonLocalVariableReadNode extends NonLocalVariableNode {
    public NonLocalVariableReadNode(final int contextLevel,
        final int upvalueIndex) {
      super(contextLevel, upvalueIndex);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object[] upvalues = determineOuterArguments(frame).getUpvalues();
      return upvalues[upvalueIndex];
    }
  }

  public static class NonLocalVariableWriteNode extends NonLocalVariableNode {
    @Child protected ExpressionNode exp;

    public NonLocalVariableWriteNode(final int contextLevel,
        final int upvalueIndex, final ExpressionNode exp) {
      super(contextLevel, upvalueIndex);
      this.exp = adoptChild(exp);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object value = exp.executeGeneric(frame);

      Object[] upvalues = determineOuterArguments(frame).getUpvalues();
      upvalues[upvalueIndex] = value;
      return value;
    }
  }
}
