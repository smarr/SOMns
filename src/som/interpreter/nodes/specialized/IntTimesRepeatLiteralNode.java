package som.interpreter.nodes.specialized;

import som.interpreter.Invokable;
import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;


@NodeChild(value = "repCnt",  type = ExpressionNode.class)
public abstract class IntTimesRepeatLiteralNode extends ExpressionNode {

  @Child protected ExpressionNode body;

  // In case we need to revert from this optimistic optimization, keep the
  // original node around
  private final ExpressionNode bodyActualNode;

  public abstract ExpressionNode getRepCnt();

  public IntTimesRepeatLiteralNode(final ExpressionNode body,
      final ExpressionNode originalBody, final SourceSection sourceSection) {
    super(sourceSection);
    this.body           = body;
    this.bodyActualNode = originalBody;
  }

  @Specialization
  public final long timesRepeat(final VirtualFrame frame, final long repCnt) {
    if (CompilerDirectives.inInterpreter()) {
      try {
        doLooping(frame, repCnt);
      } finally {
        reportLoopCount((int) repCnt);
      }
    } else {
      doLooping(frame, repCnt);
    }
    return repCnt;
  }

  protected final void doLooping(final VirtualFrame frame, final long repCnt) {
    for (long i = repCnt; i > 0; i--) {
      body.executeGeneric(frame);
    }
  }

  private void reportLoopCount(final long count) {
    if (count < 1) { return; }

    CompilerAsserts.neverPartOfCompilation("reportLoopCount");
    Node current = getParent();
    while (current != null && !(current instanceof RootNode)) {
      current = current.getParent();
    }
    if (current != null) {
      ((Invokable) current).propagateLoopCountThroughoutMethodScope(count);
    }
  }
}
