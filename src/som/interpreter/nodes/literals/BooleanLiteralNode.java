package som.interpreter.nodes.literals;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;


public abstract class BooleanLiteralNode extends LiteralNode {

  public BooleanLiteralNode(final SourceSection source) {
    super(source);
  }

  public static final class TrueLiteralNode extends BooleanLiteralNode {
    public TrueLiteralNode(final SourceSection source) { super(source); }

    @Override
    public boolean executeBoolean(final VirtualFrame frame) {
      return true;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return true;
    }
  }

  public static final class FalseLiteralNode extends BooleanLiteralNode {
    public FalseLiteralNode(final SourceSection source) { super(source); }

    @Override
    public boolean executeBoolean(final VirtualFrame frame) {
      return false;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return false;
    }
  }
}
