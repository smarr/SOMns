package som.interpreter;

import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;

public abstract class Invokable extends RootNode {

  @Child protected ExpressionNode expressionOrSequence;
  private final    ExpressionNode uninitializedBody;

  protected final int             numArguments;
  protected final FrameDescriptor frameDescriptor;

  public Invokable(final ExpressionNode expressionOrSequence,
      final int numArguments, final FrameDescriptor frameDescriptor) {
    this.uninitializedBody    = NodeUtil.cloneNode(expressionOrSequence);
    this.expressionOrSequence = adoptChild(expressionOrSequence);
    this.frameDescriptor      = frameDescriptor;

    this.numArguments         = numArguments;
  }

  public ExpressionNode getUninitializedBody() {
    return uninitializedBody;
  }

  public abstract ExpressionNode inline(final CallTarget inlinableCallTarget, final SSymbol selector);

  public abstract boolean isAlwaysToBeInlined();

  public abstract int getNumberOfUpvalues();

  public FrameDescriptor getFrameDescriptor() {
    return frameDescriptor;
  }
}
