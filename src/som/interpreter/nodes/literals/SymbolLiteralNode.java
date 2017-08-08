package som.interpreter.nodes.literals;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.vmobjects.SSymbol;


public final class SymbolLiteralNode extends LiteralNode {

  private final SSymbol value;

  public SymbolLiteralNode(final SSymbol value) {
    this.value = value;
  }

  @Override
  public SSymbol executeSSymbol(final VirtualFrame frame) {
    return value;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return value;
  }
}
