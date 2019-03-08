package som.interpreter.nodes.literals;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.vmobjects.SSymbol;
import som.vmobjects.SType;


public final class STypeLiteral extends LiteralNode {

  private final SSymbol[] sigantures;

  public STypeLiteral(final SSymbol[] sigantures) {
    this.sigantures = sigantures;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return new SType.InterfaceType(sigantures);
  }

}
