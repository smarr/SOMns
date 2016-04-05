package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.InlinerAdaptToEmbeddedOuterContext;
import som.interpreter.InlinerForLexicallyEmbeddedMethods;
import som.interpreter.SplitterForLexicallyEmbeddedCode;
import som.interpreter.nodes.ExpressionNode;
import tools.dym.Tags.LoopNode;

@NodeChildren({
  @NodeChild(value = "from",  type = ExpressionNode.class),
  @NodeChild(value = "to",  type = ExpressionNode.class)})
public abstract class IntToDoInlinedLiteralsNode extends ExpressionNode {

  @Child protected ExpressionNode body;

  // In case we need to revert from this optimistic optimization, keep the
  // original node around
  private final ExpressionNode bodyActualNode;

  private final FrameSlot loopIndex;
  private final SourceSection loopIndexSource;
  @CompilationFinal private double loopFrequency;

  public abstract ExpressionNode getFrom();
  public abstract ExpressionNode getTo();

  public IntToDoInlinedLiteralsNode(final ExpressionNode body,
      final FrameSlot loopIndex, final SourceSection loopIndexSource,
      final ExpressionNode originalBody,
      final SourceSection sourceSection) {
    super(sourceSection);
    this.body           = body;
    this.loopIndex      = loopIndex;
    this.loopIndexSource = loopIndexSource;
    this.bodyActualNode = originalBody;

    // and, we can already tell the loop index that it is going to be long
    loopIndex.setKind(FrameSlotKind.Long);
  }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == LoopNode.class) {
      return true;
    } else {
      return super.isTaggedWith(tag);
    }
  }

  @Specialization
  public final long doIntToDo(final VirtualFrame frame, final long from, final long to) {
    if (CompilerDirectives.inInterpreter()) {
      try {
        doLooping(frame, from, to);
      } finally {
        SomLoop.reportLoopCount((int) to - from, this);
      }
    } else {
      doLooping(frame, from, to);
    }
    return from;
  }

  @Specialization
  public final long doIntToDo(final VirtualFrame frame, final long from, final double to) {
    if (CompilerDirectives.inInterpreter()) {
      try {
        doLooping(frame, from, (long) to);
      } finally {
        SomLoop.reportLoopCount((int) to - from, this);
      }
    } else {
      doLooping(frame, from, (long) to);
    }
    return from;
  }

  protected final void doLooping(final VirtualFrame frame, final long from, final long to) {
    if (from <= to) {
      frame.setLong(loopIndex, from);
      body.executeGeneric(frame);
    }

    if (CompilerDirectives.inInterpreter()) {
      loopFrequency = Math.max(0.0, Math.max(loopFrequency, (to - from) / (to - from + 1.0)));
    }

    for (long i = from + 1;
        CompilerDirectives.injectBranchProbability(loopFrequency, i <= to);
        i++) {
      frame.setLong(loopIndex, i);
      body.executeGeneric(frame);
    }
  }

  @Override
  public void replaceWithLexicallyEmbeddedNode(
      final InlinerForLexicallyEmbeddedMethods inliner) {
    IntToDoInlinedLiteralsNode node = IntToDoInlinedLiteralsNodeGen.create(body,
        inliner.addLocalSlot(loopIndex.getIdentifier(), loopIndexSource), loopIndexSource,
        bodyActualNode, getSourceSection(), getFrom(), getTo());
    replace(node);
    // create loopIndex in new context...
  }

  @Override
  public void replaceWithIndependentCopyForInlining(
      final SplitterForLexicallyEmbeddedCode inliner) {
    FrameSlot inlinedLoopIdx = inliner.getLocalFrameSlot(loopIndex.getIdentifier());
    replace(IntToDoInlinedLiteralsNodeGen.create(body, inlinedLoopIdx, loopIndexSource,
        bodyActualNode, getSourceSection(), getFrom(), getTo()));
  }

  @Override
  public void replaceWithCopyAdaptedToEmbeddedOuterContext(
      final InlinerAdaptToEmbeddedOuterContext inliner) {
    // NOOP: This node has a FrameSlot, but it is local, so does not need to be updated.
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
