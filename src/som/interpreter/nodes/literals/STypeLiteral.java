package som.interpreter.nodes.literals;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.vmobjects.SType;


public final class STypeLiteral extends LiteralNode {

  private final SType type;

  public STypeLiteral(final SType type) {
    this.type = type;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return type;
  }

}
