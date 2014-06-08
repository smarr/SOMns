package som.interpreter;

import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.utilities.BranchProfile;

public abstract class Invokable extends RootNode {

  private final BranchProfile enforced;
  private final BranchProfile unenforced;

  @Child protected ExpressionNode enforcedBody;
  @Child protected ExpressionNode unenforcedBody;

  private final ExpressionNode uninitializedEnforcedBody;
  private final ExpressionNode uninitializedUnenforcedBody;

  public Invokable(final SourceSection sourceSection,
      final FrameDescriptor frameDescriptor,
      final ExpressionNode enforcedBody, final ExpressionNode unenforcedBody) {
    super(sourceSection, frameDescriptor);
    this.uninitializedEnforcedBody = NodeUtil.cloneNode(enforcedBody);
    this.enforcedBody = enforcedBody;

    this.uninitializedUnenforcedBody = NodeUtil.cloneNode(unenforcedBody);
    this.unenforcedBody = unenforcedBody;

    enforced = new BranchProfile();
    unenforced = new BranchProfile();
  }

  public ExpressionNode getUninitializedEnforcedBody() {
    return uninitializedEnforcedBody;
  }

  public ExpressionNode getUninitializedUnenforcedBody() {
    return uninitializedUnenforcedBody;
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

  public abstract Invokable cloneWithNewLexicalContext(final LexicalContext outerContext);

  @Override
  public final boolean isSplittable() {
    return true;
  }

  public final RootCallTarget createCallTarget() {
    return Truffle.getRuntime().createCallTarget(this);
  }

  public abstract void propagateLoopCountThroughoutLexicalScope(final long count);

  public abstract boolean isBlock();
}
