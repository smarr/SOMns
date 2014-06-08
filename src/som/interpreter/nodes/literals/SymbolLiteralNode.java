package som.interpreter.nodes.literals;

import som.vmobjects.SSymbol;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class SymbolLiteralNode extends LiteralNode {

  private final SSymbol value;

  public SymbolLiteralNode(final SSymbol value, final SourceSection source) {
    super(source);
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
