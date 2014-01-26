package som.interpreter;

import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;

public abstract class Invokable extends RootNode {

  @Child protected ExpressionNode  expressionOrSequence;

  private final ExpressionNode  uninitializedBody;

  public Invokable(final SourceSection sourceSection,
      final FrameDescriptor frameDescriptor,
      final ExpressionNode expressionOrSequence) {
    super(sourceSection, frameDescriptor);
    this.uninitializedBody    = NodeUtil.cloneNode(expressionOrSequence);
    this.expressionOrSequence = adoptChild(expressionOrSequence);
  }

  public ExpressionNode getUninitializedBody() {
    return uninitializedBody;
  }

  public abstract Invokable cloneWithNewLexicalContext(final LexicalContext outerContext);
  public abstract ExpressionNode inline(final RootCallTarget inlinableCallTarget, final SSymbol selector);

  public abstract boolean isAlwaysToBeInlined();

  public final RootCallTarget createCallTarget() {
    return Truffle.getRuntime().createCallTarget(this);
  }
}
