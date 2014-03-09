package som.interpreter.nodes.literals;

import static com.oracle.truffle.api.nodes.NodeInfo.Kind.SPECIALIZED;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo.Kind;

public class SymbolLiteralNode extends LiteralNode {

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

  @Override
  public Kind getKind() {
      return SPECIALIZED;
  }
}
