package bd.testsetup;

import com.oracle.truffle.api.frame.VirtualFrame;


public final class IntLiteral extends ExprNode {
  private final int value;

  public IntLiteral(final int val) {
    this.value = val;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return executeInt(frame);
  }

  @Override
  public int executeInt(final VirtualFrame frame) {
    return value;
  }
}
