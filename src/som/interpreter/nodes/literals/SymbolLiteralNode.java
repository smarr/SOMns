package som.interpreter.nodes.literals;

import som.vmobjects.Object;
import som.vmobjects.Symbol;

import com.oracle.truffle.api.frame.VirtualFrame;


public class SymbolLiteralNode extends LiteralNode {

  private final Symbol value;

  public SymbolLiteralNode(final Symbol value) {
    this.value = value;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return value;
  }
}
