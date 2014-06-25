package som.interpreter;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;


public abstract class AbstractInvokable extends RootNode {

  public AbstractInvokable(final SourceSection sourceSection,
      final FrameDescriptor frameDescriptor) {
    super(sourceSection, frameDescriptor);
  }

  public abstract AbstractInvokable cloneWithNewLexicalContext(
      final LexicalContext outerContext);

  @Override
  public final boolean isSplittable() {
    return true;
  }

  public final RootCallTarget createCallTarget() {
    return Truffle.getRuntime().createCallTarget(this);
  }

  public abstract void propagateLoopCountThroughoutLexicalScope(final long count);

  public abstract boolean isBlock();
  public abstract boolean isUnenforced();
  public abstract boolean isEmptyPrimitive();

  public abstract void setOuterContextMethod(final AbstractInvokable method);
}
