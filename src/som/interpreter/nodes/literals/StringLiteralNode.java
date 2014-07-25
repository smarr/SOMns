package som.interpreter.nodes.literals;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;


public final class StringLiteralNode extends LiteralNode {

  private final String value;

  public StringLiteralNode(final String value, final SourceSection source,
      final boolean executesEnforced) {
    super(source, executesEnforced);
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
}
