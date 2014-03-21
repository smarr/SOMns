package som.interpreter.nodes.literals;

import com.oracle.truffle.api.frame.VirtualFrame;


public final class IntegerLiteralNode extends LiteralNode {

  private final int value;

  public IntegerLiteralNode(final int value) {
    this.value = value;
  }

  @Override
  public int executeInteger(final VirtualFrame frame) {
    return value;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return value;
  }
}
