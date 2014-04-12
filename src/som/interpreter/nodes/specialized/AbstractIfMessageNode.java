package som.interpreter.nodes.specialized;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.CallNode;
import com.oracle.truffle.api.utilities.BranchProfile;


public abstract class AbstractIfMessageNode extends BinaryExpressionNode {
  protected final BranchProfile ifFalseBranch = new BranchProfile();
  protected final BranchProfile ifTrueBranch  = new BranchProfile();

  private final SInvokable branchMethod;

  @Child protected CallNode branchValueSend;

  protected final Universe universe;

  public AbstractIfMessageNode(final Object rcvr, final Object arg,
      final Universe universe) {
    if (arg instanceof SBlock) {
      SBlock argBlock = (SBlock) arg;
      branchMethod = argBlock.getMethod();
      branchValueSend = Truffle.getRuntime().createCallNode(
          branchMethod.getCallTarget());
    } else {
      branchMethod = null;
    }
    this.universe = universe;
  }

  public AbstractIfMessageNode(final AbstractIfMessageNode node) {
    branchMethod = node.branchMethod;
    if (node.branchMethod != null) {
      branchValueSend = Truffle.getRuntime().createCallNode(
          branchMethod.getCallTarget());
    }
    this.universe = node.universe;
  }

  /**
   * This is the case were we got a block as the argument. Need to actually
   * evaluate it.
   */
  public final Object doIfWithInlining(final VirtualFrame frame, final SObject receiver,
      final SBlock argument, final SObject predicateObject) {
    if (receiver == predicateObject) {
      ifTrueBranch.enter();
      return branchValueSend.call(frame, new Object[] {argument});
    } else {
      ifFalseBranch.enter();
      return universe.nilObject;
    }
  }

  public final Object doIf(final VirtualFrame frame, final SObject receiver,
      final SBlock argument, final SObject predicateObject) {
    if (receiver == predicateObject) {
      ifTrueBranch.enter();
      return argument.getMethod().invoke(argument);
    } else {
      ifFalseBranch.enter();
      return universe.nilObject;
    }
  }

  protected final boolean isSameArgument(final Object receiver, final SBlock argument) {
    return branchMethod == null || argument.getMethod() == branchMethod;
  }
}
