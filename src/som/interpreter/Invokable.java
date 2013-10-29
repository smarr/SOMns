package som.interpreter;

import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SAbstractObject;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public abstract class Invokable extends RootNode {

  @Child protected ExpressionNode expressionOrSequence;

  protected final FrameSlot   selfSlot;
  @CompilationFinal protected final FrameSlot[]  argumentSlots;

  protected final FrameDescriptor frameDescriptor;

  public Invokable(final ExpressionNode expressionOrSequence,
      final FrameSlot selfSlot, final FrameSlot[] arugmentSlots,
      final FrameDescriptor frameDescriptor) {
    this.expressionOrSequence = adoptChild(expressionOrSequence);
    this.selfSlot             = selfSlot;
    this.argumentSlots        = arugmentSlots;
    this.frameDescriptor      = frameDescriptor;
  }

  public abstract SAbstractObject executeInlined(final VirtualFrame frame,
      final ExpressionNode exp);

  public abstract ExpressionNode methodCloneForInlining();

  public FrameDescriptor getFrameDescriptor() {
    return frameDescriptor;
  }
}
