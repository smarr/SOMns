package som.interpreter.nodes.literals;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;


public final class IntegerLiteralNode extends LiteralNode {

  private final long value;

  public IntegerLiteralNode(final long value, final SourceSection source,
      final boolean executesEnforced) {
    super(source, executesEnforced);
    this.value = value;
  }

  @Override
  public long executeLong(final VirtualFrame frame) {
    return value;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return value;
  }
}
