package som.interpreter;

import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;

public abstract class Invokable extends RootNode {

  @Child protected ExpressionNode  expressionOrSequence;
  protected final  FrameDescriptor frameDescriptor;

  private final ExpressionNode  uninitializedBody;

  public Invokable(final ExpressionNode expressionOrSequence,
      final FrameDescriptor frameDescriptor) {
    this.uninitializedBody    = NodeUtil.cloneNode(expressionOrSequence);
    this.expressionOrSequence = adoptChild(expressionOrSequence);
    this.frameDescriptor      = frameDescriptor;
  }

  public ExpressionNode getUninitializedBody() {
    return uninitializedBody;
  }

  public abstract Invokable cloneWithNewLexicalContext(final LexicalContext outerContext);
  public abstract ExpressionNode inline(final CallTarget inlinableCallTarget, final SSymbol selector);

  public abstract boolean isAlwaysToBeInlined();

  public final CallTarget createCallTarget() {
    TruffleRuntime runtime = Truffle.getRuntime();
    return runtime.createCallTarget(this, frameDescriptor);
  }
}
