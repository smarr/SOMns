package som.interpreter;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.MethodBuilder;
import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SInvokable;


public abstract class Invokable extends RootNode {
  protected final String      name;
  private final SourceSection sourceSection;

  /** Marks this invokable as being used in a transactional context. */
  protected final boolean isAtomic;

  @Child protected ExpressionNode expressionOrSequence;

  protected final ExpressionNode uninitializedBody;

  public Invokable(final String name,
      final SourceSection sourceSection,
      final FrameDescriptor frameDescriptor,
      final ExpressionNode expressionOrSequence,
      final ExpressionNode uninitialized,
      final boolean isAtomic, final SomLanguage lang) {
    super(lang, frameDescriptor);
    this.name = name;
    this.expressionOrSequence = expressionOrSequence;
    this.uninitializedBody = uninitialized;
    this.isAtomic = isAtomic;
    this.sourceSection = sourceSection;
  }

  @Override
  public String getName() {
    return name;
  }

  public final boolean isAtomic() {
    return isAtomic;
  }

  @Override
  public final Object execute(final VirtualFrame frame) {
    return expressionOrSequence.executeGeneric(frame);
  }

  /** Inline invokable into the lexical context of the given builder. */
  public abstract ExpressionNode inline(MethodBuilder builder, SInvokable outer);

  /**
   * Create a version of the invokable that can be used in a
   * transactional context.
   */
  public abstract Invokable createAtomic();

  @Override
  public final boolean isCloningAllowed() {
    return true;
  }

  @Override
  protected final boolean isCloneUninitializedSupported() {
    return true;
  }

  @Override
  protected final RootNode cloneUninitialized() {
    return (RootNode) deepCopy();
  }

  public final RootCallTarget createCallTarget() {
    return Truffle.getRuntime().createCallTarget(this);
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }

  public abstract void propagateLoopCountThroughoutMethodScope(long count);
}
