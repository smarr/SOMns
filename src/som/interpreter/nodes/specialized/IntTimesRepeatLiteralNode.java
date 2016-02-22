package som.interpreter.nodes.specialized;

import som.compiler.Tags;
import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import dym.Tagging;


@NodeChild(value = "repCnt",  type = ExpressionNode.class)
public abstract class IntTimesRepeatLiteralNode extends ExpressionNode {

  @Child protected ExpressionNode body;

  // In case we need to revert from this optimistic optimization, keep the
  // original node around
  @SuppressWarnings("unused") private final ExpressionNode bodyActualNode;
  @CompilationFinal private double loopFrequency;

  public abstract ExpressionNode getRepCnt();

  public IntTimesRepeatLiteralNode(final ExpressionNode body,
      final ExpressionNode originalBody, final SourceSection sourceSection) {
    super(Tagging.cloneAndAddTags(sourceSection, Tags.LOOP_NODE));
    this.body           = body;
    this.bodyActualNode = originalBody;
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

    for (long i = repCnt;
        CompilerDirectives.injectBranchProbability(loopFrequency, i > 0);
        i--) {
      body.executeGeneric(frame);
    }
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
