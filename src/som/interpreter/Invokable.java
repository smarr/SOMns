package som.interpreter;

import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;

public abstract class Invokable extends AbstractInvokable {

  @Child protected ExpressionNode body;

  protected final ExpressionNode uninitializedBody;

  public Invokable(final SourceSection sourceSection,
      final FrameDescriptor frameDescriptor, final ExpressionNode body,
      final boolean executesEnforced) {
    super(sourceSection, frameDescriptor, executesEnforced);
    this.uninitializedBody = NodeUtil.cloneNode(body);
    this.body = body;
    assert body.nodeExecutesEnforced() == executesEnforced;
  }

  @Override
  public final Object execute(final VirtualFrame frame) {
    return body.executeGeneric(frame);
  }
}
