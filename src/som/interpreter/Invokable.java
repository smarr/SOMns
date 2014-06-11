package som.interpreter;

import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.utilities.BranchProfile;

public abstract class Invokable extends AbstractInvokable {

  private final BranchProfile enforced;
  private final BranchProfile unenforced;

  @Child protected ExpressionNode enforcedBody;
  @Child protected ExpressionNode unenforcedBody;

  protected final ExpressionNode uninitializedEnforcedBody;
  protected final ExpressionNode uninitializedUnenforcedBody;

  public Invokable(final SourceSection sourceSection,
      final FrameDescriptor frameDescriptor,
      final ExpressionNode enforcedBody, final ExpressionNode unenforcedBody) {
    super(sourceSection, frameDescriptor);
    this.uninitializedEnforcedBody = NodeUtil.cloneNode(enforcedBody);
    this.enforcedBody = enforcedBody;

    this.uninitializedUnenforcedBody = NodeUtil.cloneNode(unenforcedBody);
    this.unenforcedBody = unenforcedBody;

    enforced   = new BranchProfile();
    unenforced = new BranchProfile();
  }

  @Override
  public final Object execute(final VirtualFrame frame) {
    if (SArguments.enforced(frame)) {
      enforced.enter();
      return enforcedBody.executeGeneric(frame);
    } else {
      unenforced.enter();
      return unenforcedBody.executeGeneric(frame);
    }
  }
}
