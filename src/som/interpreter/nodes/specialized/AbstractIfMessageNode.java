package som.interpreter.nodes.specialized;

import som.interpreter.SArguments;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.utilities.BranchProfile;


public abstract class AbstractIfMessageNode extends BinaryExpressionNode {
  protected final BranchProfile ifFalseBranch = new BranchProfile();
  protected final BranchProfile ifTrueBranch  = new BranchProfile();

  private final SInvokable branchMethod;

  @Child protected DirectCallNode branchValueSend;
  private final boolean branchEnforced;

  protected final Universe universe;

  public AbstractIfMessageNode(final Object rcvr, final Object arg,
      final Universe universe, final SourceSection source, final boolean executesEnforced) {
    super(source, executesEnforced);
    if (arg instanceof SBlock) {
      SBlock argBlock = (SBlock) arg;
      branchMethod = argBlock.getMethod();
      branchValueSend = Truffle.getRuntime().createDirectCallNode(
          branchMethod.getCallTarget());
      branchEnforced = argBlock.isEnforced();
    } else {
      branchMethod = null;
      branchEnforced = false; // TODO: does this even matter?
    }
    this.universe = universe;
  }

  public AbstractIfMessageNode(final AbstractIfMessageNode node) {
    super(node.getSourceSection(), node.executesEnforced);
    branchMethod = node.branchMethod;
    branchEnforced = node.branchEnforced;
    if (node.branchMethod != null) {
      branchValueSend = Truffle.getRuntime().createDirectCallNode(
          branchMethod.getCallTarget());
    }
    this.universe = node.universe;
  }

  /**
   * This is the case were we got a block as the argument. Need to actually
   * evaluate it.
   */
  public final Object doIfWithInlining(final VirtualFrame frame,
      final boolean receiver, final SBlock argument, final boolean predicateObject) {
    if (receiver == predicateObject) {
      ifTrueBranch.enter();
      SObject domain   = SArguments.domain(frame);
      return branchValueSend.call(frame, SArguments.createSArguments(domain, branchEnforced, new Object[] {argument}));
    } else {
      ifFalseBranch.enter();
      return universe.nilObject;
    }
  }

  public final Object doIf(final VirtualFrame frame, final boolean receiver,
      final SBlock argument, final boolean predicateObject) {
    if (receiver == predicateObject) {
      ifTrueBranch.enter();
      CompilerAsserts.neverPartOfCompilation();
      SObject domain   = SArguments.domain(frame);
      return argument.getMethod().invoke(domain, branchEnforced, argument);
    } else {
      ifFalseBranch.enter();
      return universe.nilObject;
    }
  }

  protected final boolean isSameArgument(final Object receiver, final SBlock argument) {
    return branchMethod == null || argument.getMethod() == branchMethod;
  }
}
