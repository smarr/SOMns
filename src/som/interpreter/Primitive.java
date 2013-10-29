package som.interpreter;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.PrimitiveNode;
import som.vmobjects.SAbstractObject;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;


public class Primitive extends Invokable {

  public Primitive(final PrimitiveNode primitive,
      final FrameSlot selfSlot, final FrameSlot[] arugmentSlots,
      final FrameDescriptor frameDescriptor) {
    super(primitive, selfSlot, arugmentSlots, frameDescriptor);
  }

  @Override
  public Object execute(final VirtualFrame frame) {
    initializeFrame(frame);
    return expressionOrSequence.executeGeneric(frame);
  }

  @Override
  public ExpressionNode methodCloneForInlining() {
    return expressionOrSequence.cloneForInlining();
  }

  @ExplodeLoop
  protected void initializeFrame(final VirtualFrame frame) {
    frame.setObject(selfSlot, frame.getArguments(Arguments.class).getSelf());

    Arguments args = frame.getArguments(Arguments.class);
    for (int i = 0; i < argumentSlots.length; i++) {
      frame.setObject(argumentSlots[i], args.getArgument(i));
    }
  }

  @Override
  public SAbstractObject executeInlined(final VirtualFrame frame,
      final ExpressionNode exp) {
    initializeFrame(frame);
    return exp.executeGeneric(frame);
  }

  @Override
  public String toString() {
    return "Primitive " + expressionOrSequence.getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
  }

}
