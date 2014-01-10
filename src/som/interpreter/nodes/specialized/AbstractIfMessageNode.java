package som.interpreter.nodes.specialized;

import som.interpreter.BlockHelper;
import static som.interpreter.BlockHelper.createInlineableNode;
import som.interpreter.nodes.BinaryMessageNode;
import som.interpreter.nodes.UnaryMessageNode;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.utilities.BranchProfile;


public abstract class AbstractIfMessageNode extends BinaryMessageNode {
  protected final BranchProfile ifFalseBranch = new BranchProfile();
  protected final BranchProfile ifTrueBranch  = new BranchProfile();

  private final SMethod branchMethod;

  @Child protected UnaryMessageNode branchValueSend;

  public AbstractIfMessageNode(final BinaryMessageNode node, final Object rcvr,
      final Object arg) {
    super(node);
    if (arg instanceof SBlock) {
      SBlock argBlock = (SBlock) arg;
      branchMethod = argBlock.getMethod();
      branchValueSend = adoptChild(createInlineableNode(branchMethod, universe));
    } else {
      branchMethod = null;
    }
  }

  public AbstractIfMessageNode(final AbstractIfMessageNode node) {
    super(node);
    branchMethod = node.branchMethod;
    if (node.branchMethod != null) {
      branchValueSend = adoptChild(createInlineableNode(branchMethod, universe));
    }
  }

  /**
   * This is the case were we got a block as the argument. Need to actually
   * evaluate it.
   */
  public final Object doIfWithInlining(final VirtualFrame frame, final SObject receiver,
      final SBlock argument, final SObject predicateObject) {
    if (receiver == predicateObject) {
      ifTrueBranch.enter();
      return branchValueSend.executeEvaluated(frame, BlockHelper.createBlock(argument, universe));
    } else {
      ifFalseBranch.enter();
      return universe.nilObject;
    }
  }

  public final Object doIf(final VirtualFrame frame, final SObject receiver,
      final SBlock argument, final SObject predicateObject) {
    if (receiver == predicateObject) {
      ifTrueBranch.enter();
      return argument.getMethod().invoke(frame.pack(), BlockHelper.createBlock(argument, universe), universe);
    } else {
      ifFalseBranch.enter();
      return universe.nilObject;
    }
  }

  protected final boolean isSameArgument(final Object receiver, final SBlock argument) {
    return branchMethod == null || argument.getMethod() == branchMethod;
  }
}
