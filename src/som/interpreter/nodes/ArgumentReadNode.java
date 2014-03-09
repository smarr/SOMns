package som.interpreter.nodes;

import som.interpreter.SArguments;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo.Kind;

public final class ArgumentReadNode extends ExpressionNode {
  protected final int argumentIndex;

  public ArgumentReadNode(final int argumentIndex) {
    this.argumentIndex = argumentIndex;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return SArguments.getArgumentsFromFrame(frame)[argumentIndex];
  }

  public static final class SelfArgumentReadNode extends ExpressionNode {
    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return SArguments.getReceiverFromFrame(frame);
    }
  }

  @Override
  public Kind getKind() {
      return Kind.GENERIC;
  }
}
