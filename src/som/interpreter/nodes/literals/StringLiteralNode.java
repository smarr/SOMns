package som.interpreter.nodes.literals;

import static com.oracle.truffle.api.nodes.NodeInfo.Kind.SPECIALIZED;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo.Kind;


public final class StringLiteralNode extends LiteralNode {

  private final String value;

  public StringLiteralNode(final String value) {
    this.value = value;
  }

  @Override
  public String executeString(final VirtualFrame frame) {
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
