package som.interpreter;

import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeUtil;

public abstract class Invokable extends AbstractInvokable {
  private final boolean alwaysInline;

  @Child protected ExpressionNode body;

  protected final ExpressionNode uninitializedBody;

  public Invokable(final SourceSection sourceSection,
      final FrameDescriptor frameDescriptor, final ExpressionNode body,
      final boolean executesEnforced,
      final boolean alwaysInline) {
    super(sourceSection, frameDescriptor, executesEnforced);
    this.uninitializedBody = NodeUtil.cloneNode(body);
    this.body = body;
    assert body.nodeExecutesEnforced() == executesEnforced;

    this.alwaysInline = alwaysInline;
  }

  @Override
  public boolean alwaysInline() {
    return alwaysInline;
  }

  @Override
  public final Object execute(final VirtualFrame frame) {
    return body.executeGeneric(frame);
  }
}
