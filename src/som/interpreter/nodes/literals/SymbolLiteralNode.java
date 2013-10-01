package som.interpreter.nodes.literals;

import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;


public class SymbolLiteralNode extends LiteralNode {

  private final SSymbol value;

  public SymbolLiteralNode(final SSymbol value) {
    this.value = value;
  }

  @Override
  public SObject executeGeneric(VirtualFrame frame) {
    return value;
  }
}
