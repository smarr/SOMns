package som.interpreter;

import som.interpreter.nodes.ExpressionNode;
import som.vm.Universe;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;


public final class MethodUnenforced extends InvokableUnenforced {
  private final Universe universe;
  private final LexicalContext outerContext;

  public MethodUnenforced(final SourceSection sourceSection,
      final FrameDescriptor frameDescriptor,
      final ExpressionNode unenforcedBody,
      final Universe universe,
      final LexicalContext outerContext) {
    super(sourceSection, frameDescriptor, unenforcedBody);
    this.universe     = universe;
    this.outerContext = outerContext;
  }

  @Override
  public String toString() {
    SourceSection ss = getSourceSection();
    final String name = ss.getIdentifier();
    final String location = getSourceSection().toString();
    return "Method " + name + ":" + location + "@" + Integer.toHexString(hashCode());
  }

  @Override
  public AbstractInvokable cloneWithNewLexicalContext(final LexicalContext outerContext) {
    FrameDescriptor inlinedFrameDescriptor = getFrameDescriptor().copy();
    LexicalContext  inlinedContext = new LexicalContext(inlinedFrameDescriptor,
        outerContext);
    ExpressionNode  inlinedUnenforcedBody = Inliner.doInline(
        uninitializedUnenforcedBody, inlinedContext);
    MethodUnenforced clone = new MethodUnenforced(getSourceSection(),
        inlinedFrameDescriptor, inlinedUnenforcedBody, universe, outerContext);
    inlinedContext.setOuterMethod(clone);
    return clone;
  }

  @Override
  public boolean isBlock() {
    return outerContext != null;
  }

  @Override
  public boolean isEmptyPrimitive() {
    return false;
  }

  @Override
  public void setOuterContextMethod(final AbstractInvokable method) {
    outerContext.setOuterMethod(method);
  }

  @Override
  public void propagateLoopCountThroughoutLexicalScope(final long count) {
    assert count >= 0;

    if (outerContext != null) {
      outerContext.getOuterMethod().propagateLoopCountThroughoutLexicalScope(count);
    }
    reportLoopCount((count > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) count);
  }

  @Override
  public RootNode split() {
    return cloneWithNewLexicalContext(outerContext);
  }
}
