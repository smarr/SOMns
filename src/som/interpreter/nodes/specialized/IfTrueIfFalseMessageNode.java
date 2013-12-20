package som.interpreter.nodes.specialized;

import som.interpreter.Arguments;
import som.interpreter.nodes.messages.TernarySendNode;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.utilities.BranchProfile;


public abstract class IfTrueIfFalseMessageNode extends TernarySendNode {

  public IfTrueIfFalseMessageNode(final TernarySendNode node) { super(node); }
  public IfTrueIfFalseMessageNode(final IfTrueIfFalseMessageNode node) { super(node); }

  private final BranchProfile ifFalseBranch = new BranchProfile();
  private final BranchProfile ifTrueBranch  = new BranchProfile();

  @Specialization(order = 1)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final SBlock falseBlock) {
    SMethod branchMethod;
    Arguments context;
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      branchMethod = trueBlock.getMethod();
      context      = trueBlock.getContext(); // TODO: test whether the current implementation is correct, or whether it should be the following: Method.getUpvalues(frame);
    } else {
      assert receiver == universe.falseObject;
      ifFalseBranch.enter();
      branchMethod = falseBlock.getMethod();
      context      = falseBlock.getContext(); // TODO: test whether the current implementation is correct, or whether it should be the following: Method.getUpvalues(frame);
    }

    SBlock b = universe.newBlock(branchMethod, context);
    return branchMethod.invoke(frame.pack(), b, universe);
  }

  @Specialization(order = 2)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final Object trueValue, final SBlock falseBlock) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      return trueValue;
    } else {
      assert receiver == universe.falseObject;
      ifFalseBranch.enter();
      SMethod   branchMethod = falseBlock.getMethod();
      Arguments context      = falseBlock.getContext(); // TODO: test whether the current implementation is correct, or whether it should be the following: Method.getUpvalues(frame);
      SBlock b = universe.newBlock(branchMethod, context);
      return branchMethod.invoke(frame.pack(), b, universe);
    }
  }

  @Specialization(order = 3)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final Object falseValue) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      SMethod branchMethod = trueBlock.getMethod();
      Arguments context    = trueBlock.getContext(); // TODO: test whether the current implementation is correct, or whether it should be the following: Method.getUpvalues(frame);
      SBlock  b = universe.newBlock(branchMethod, context);
      return branchMethod.invoke(frame.pack(), b, universe);
    } else {
      ifFalseBranch.enter();
      assert receiver == universe.falseObject;
      return falseValue;
    }
  }

  @Specialization(order = 4)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final Object trueValue, final Object falseValue) {
    if (receiver == universe.trueObject) {
      return trueValue;
    } else {
      assert receiver == universe.falseObject;
      return falseValue;
    }
  }
}
