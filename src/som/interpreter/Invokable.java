package som.interpreter;

import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;

public abstract class Invokable extends RootNode {

  @Child protected ExpressionNode expressionOrSequence;
  private final    ExpressionNode uninitializedBody;

  @CompilationFinal protected final FrameSlot[]  argumentSlots;

  protected final FrameDescriptor frameDescriptor;

  public Invokable(final ExpressionNode expressionOrSequence,
      final FrameSlot[] argumentSlots,
      final FrameDescriptor frameDescriptor) {
    this.uninitializedBody    = NodeUtil.cloneNode(expressionOrSequence);
    this.expressionOrSequence = adoptChild(expressionOrSequence);
    this.argumentSlots        = argumentSlots;
    this.frameDescriptor      = frameDescriptor;
  }

  public ExpressionNode getUninitializedBody() {
    return uninitializedBody;
  }

  public abstract ExpressionNode inline(final CallTarget inlinableCallTarget, final SSymbol selector);

  public abstract boolean isAlwaysToBeInlined();

  public FrameDescriptor getFrameDescriptor() {
    return frameDescriptor;
  }
}
