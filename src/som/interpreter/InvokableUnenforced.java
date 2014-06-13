package som.interpreter;

import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeUtil;


public abstract class InvokableUnenforced extends AbstractInvokable {
  @Child protected ExpressionNode unenforcedBody;
  protected final ExpressionNode uninitializedUnenforcedBody;

  public InvokableUnenforced(final SourceSection sourceSection,
      final FrameDescriptor frameDescriptor,
      final ExpressionNode unenforcedBody) {
    super(sourceSection, frameDescriptor);
    this.uninitializedUnenforcedBody = NodeUtil.cloneNode(unenforcedBody);
    this.unenforcedBody = unenforcedBody;
  }

  @Override
  public boolean isUnenforced() {
    return true;
  }

  @Override
  public final Object execute(final VirtualFrame frame) {
    return unenforcedBody.executeGeneric(frame);
  }
}
