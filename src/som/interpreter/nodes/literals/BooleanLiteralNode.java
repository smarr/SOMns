package som.interpreter.nodes.literals;

import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class BooleanLiteralNode extends LiteralNode {

  public static final class TrueLiteralNode extends BooleanLiteralNode {

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
