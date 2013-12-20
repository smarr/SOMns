package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

public class SelfReadNode extends ContextualNode {
  public SelfReadNode(final int contextLevel) {
    super(contextLevel);
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return determineOuterSelf(frame);
  }

  public static class SuperReadNode extends SelfReadNode {
    public SuperReadNode(final int contextLevel) {
      super(contextLevel); }
  }
}
