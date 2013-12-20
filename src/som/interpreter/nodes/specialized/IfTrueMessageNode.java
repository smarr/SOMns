package som.interpreter.nodes.specialized;

import som.interpreter.Arguments;
import som.interpreter.nodes.messages.BinarySendNode;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.utilities.BranchProfile;


public abstract class IfTrueMessageNode extends BinarySendNode {
  public IfTrueMessageNode(final BinarySendNode node) { super(node); }
  public IfTrueMessageNode(final IfTrueMessageNode node) { super(node); }

  private final BranchProfile ifFalseBranch = new BranchProfile();
  private final BranchProfile ifTrueBranch  = new BranchProfile();

  /**
   * This is the case were we got a block as the argument. Need to actually
   * evaluate it.
   */
  @Specialization
  public Object doIfTrue(final VirtualFrame frame, final SObject receiver,
      final SBlock argument) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      SMethod   blockMethod = argument.getMethod();
      Arguments context     = argument.getContext(); // TODO: test whether the current implementation is correct, or whether it should be the following: Method.getUpvalues(frame);
      SBlock b = universe.newBlock(blockMethod, context);
      return blockMethod.invoke(frame.pack(), b, universe);
    } else {
      ifFalseBranch.enter();
      return universe.nilObject;
    }
  }

  /**
   * The argument in this case is an expression and can be returned directly.
   */
  @Specialization
  public Object doIfTrue(final VirtualFrame frame,
      final SObject receiver, final Object argument) {
    if (receiver == universe.trueObject) {
      return argument;
    } else {
      return universe.nilObject;
    }
  }

}
