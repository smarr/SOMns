package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;

import bd.inlining.Inline;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import tools.dym.Tags.LoopNode;


@Inline(selector = "timesRepeat:", inlineableArgIdx = 1, disabled = true)
@NodeChild(value = "repCnt", type = ExpressionNode.class)
@GenerateNodeFactory
public abstract class IntTimesRepeatLiteralNode extends ExprWithTagsNode {

  @Child protected ExpressionNode body;

  // In case we need to revert from this optimistic optimization, keep the
  // original node around
  @SuppressWarnings("unused") private final ExpressionNode bodyActualNode;
  @CompilationFinal private double                         loopFrequency;

  public abstract ExpressionNode getRepCnt();

  public IntTimesRepeatLiteralNode(final ExpressionNode originalBody,
      final ExpressionNode body) {
    this.body = body;
    this.bodyActualNode = originalBody;
    body.markAsLoopBody();
  }

  @Override
  public boolean hasTag(final Class<? extends Tag> tag) {
    if (tag == LoopNode.class) {
      return true;
    } else {
      return super.hasTag(tag);
    }
  }

  @Specialization
  public final long timesRepeat(final VirtualFrame frame, final long repCnt) {
    if (CompilerDirectives.inInterpreter()) {
      try {
        doLooping(frame, repCnt);
      } finally {
        SomLoop.reportLoopCount((int) repCnt, this);
      }
    } else {
      doLooping(frame, repCnt);
    }
    return repCnt;
  }

  protected final void doLooping(final VirtualFrame frame, final long repCnt) {
    if (CompilerDirectives.inInterpreter()) {
      loopFrequency = Math.max(loopFrequency, repCnt / (repCnt + 1.0));
    }

    for (long i = repCnt; CompilerDirectives.injectBranchProbability(loopFrequency,
        i > 0); i--) {
      body.executeGeneric(frame);
      ObjectTransitionSafepoint.INSTANCE.checkAndPerformSafepoint();
    }
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
