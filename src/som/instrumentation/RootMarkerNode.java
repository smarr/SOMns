package som.instrumentation;

import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.source.SourceSection;


@Instrumentable(factory = RootMarkerNodeWrapper.class)
public class RootMarkerNode extends ExpressionNode {

  @Child protected ExpressionNode body;

  public RootMarkerNode(final SourceSection sourceSection, final ExpressionNode body) {
    super(sourceSection);
    this.body = body;
  }

  protected RootMarkerNode(final RootMarkerNode wrappedNode) {
    this(wrappedNode.getSourceSection(), wrappedNode.body);
  }

  protected RootMarkerNode(final SourceSection sourceSection) {
    super(sourceSection);
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return body.executeGeneric(frame);
  }
}
