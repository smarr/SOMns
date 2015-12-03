package som.interpreter.nodes.specialized;

import som.interpreter.InlinerAdaptToEmbeddedOuterContext;
import som.interpreter.InlinerForLexicallyEmbeddedMethods;
import som.interpreter.Invokable;
import som.interpreter.SplitterForLexicallyEmbeddedCode;
import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

@NodeChildren({
  @NodeChild(value = "from",  type = ExpressionNode.class),
  @NodeChild(value = "to",  type = ExpressionNode.class)})
public abstract class IntToDoInlinedLiteralsNode extends ExpressionNode {

  @Child protected ExpressionNode body;

  // In case we need to revert from this optimistic optimization, keep the
  // original node around
  private final ExpressionNode bodyActualNode;

  private final FrameSlot loopIndex;

  public abstract ExpressionNode getFrom();
  public abstract ExpressionNode getTo();

  public IntToDoInlinedLiteralsNode(final ExpressionNode body,
      final FrameSlot loopIndex, final ExpressionNode originalBody,
      final SourceSection sourceSection) {
    super(sourceSection);
    this.body           = body;
    this.loopIndex      = loopIndex;
    this.bodyActualNode = originalBody;

    // and, we can already tell the loop index that it is going to be long
    loopIndex.setKind(FrameSlotKind.Long);
  }


  @Specialization
  public final long doIntToDo(final VirtualFrame frame, final long from, final long to) {
    if (CompilerDirectives.inInterpreter()) {
      try {
        doLooping(frame, from, to);
      } finally {
        reportLoopCount((int) to - from);
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
        reportLoopCount((int) to - from);
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

    double probability = (to - from) / (to - from + 1.0);
    for (long i = from + 1;
        CompilerDirectives.injectBranchProbability(probability, i <= to);
        i++) {
      frame.setLong(loopIndex, i);
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


  @Override
  public void replaceWithLexicallyEmbeddedNode(
      final InlinerForLexicallyEmbeddedMethods inliner) {
    IntToDoInlinedLiteralsNode node = IntToDoInlinedLiteralsNodeGen.create(body,
        inliner.addLocalSlot(loopIndex.getIdentifier()),
        bodyActualNode, getSourceSection(), getFrom(), getTo());
    replace(node);
    // create loopIndex in new context...
  }

  @Override
  public void replaceWithIndependentCopyForInlining(
      final SplitterForLexicallyEmbeddedCode inliner) {
    FrameSlot inlinedLoopIdx = inliner.getLocalFrameSlot(loopIndex.getIdentifier());
    replace(IntToDoInlinedLiteralsNodeGen.create(body, inlinedLoopIdx,
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
