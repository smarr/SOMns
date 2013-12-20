package som.interpreter.nodes.specialized;

import som.interpreter.nodes.messages.BinarySendNode;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.utilities.BranchProfile;


public abstract class IfFalseMessageNode extends BinarySendNode  {
  public IfFalseMessageNode(final BinarySendNode node) { super(node); }
  public IfFalseMessageNode(final IfFalseMessageNode node) { super(node); }

  private final BranchProfile ifFalseBranch = new BranchProfile();
  private final BranchProfile ifTrueBranch  = new BranchProfile();

  /**
   * This is the case were we got a block as the argument. Need to actually
   * evaluate it.
   */
  @Specialization
  public Object doIfFalse(final VirtualFrame frame,
      final SObject receiver, final SBlock argument) {
    if (receiver == universe.falseObject) {
      ifFalseBranch.enter();
      SMethod blockMethod = argument.getMethod();
      // TODO: test whether the current implementation is correct, or whether it should be the following: Method.getUpvalues(frame);
      SBlock b = universe.newBlock(blockMethod, argument.getContext());
      return blockMethod.invoke(frame.pack(), b, universe);
    } else {
      ifTrueBranch.enter();
      return universe.nilObject;
    }
  }

  /**
   * The argument in this case is an expression and can be returned directly.
   */
  @Specialization
  public Object doIfFalse(final VirtualFrame frame,
      final SObject receiver, final Object argument) {
    if (receiver == universe.falseObject) {
      return argument;
    } else {
      return universe.nilObject;
    }
  }
}
