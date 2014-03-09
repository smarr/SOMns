package som.interpreter.nodes.literals;

import static com.oracle.truffle.api.nodes.NodeInfo.Kind.SPECIALIZED;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo.Kind;


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

  @Override
  public Kind getKind() {
      return SPECIALIZED;
  }
}
