package som.interpreter.nodes.specialized;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vm.constants.Nil;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.ConditionProfile;


public abstract class AbstractIfMessageNode extends BinaryExpressionNode {
  protected final ConditionProfile condProf = ConditionProfile.createCountingProfile();

  private final SInvokable branchMethod;

  @Child protected DirectCallNode branchValueSend;
  @Child private   IndirectCallNode call;

  public AbstractIfMessageNode(final Object rcvr, final Object arg,
      final SourceSection source) {
    super(source);
    if (arg instanceof SBlock) {
      SBlock argBlock = (SBlock) arg;
      branchMethod = argBlock.getMethod();
      branchValueSend = Truffle.getRuntime().createDirectCallNode(
          branchMethod.getCallTarget());
    } else {
      branchMethod = null;
    }
    call = Truffle.getRuntime().createIndirectCallNode();
  }

  public AbstractIfMessageNode(final AbstractIfMessageNode node) {
    super(node.getSourceSection());
    branchMethod = node.branchMethod;
    if (node.branchMethod != null) {
      branchValueSend = Truffle.getRuntime().createDirectCallNode(
          branchMethod.getCallTarget());
    }
    call = Truffle.getRuntime().createIndirectCallNode();
  }

  /**
   * This is the case were we got a block as the argument. Need to actually
   * evaluate it.
   */
  public final Object doIfWithInlining(final VirtualFrame frame,
      final boolean receiver, final SBlock argument, final boolean predicateObject) {
    if (condProf.profile(receiver == predicateObject)) {
      return branchValueSend.call(frame, new Object[] {argument});
    } else {
      return Nil.nilObject;
    }
  }

  public final Object doIf(final VirtualFrame frame, final boolean receiver,
      final SBlock argument, final boolean predicateObject) {
    if (condProf.profile(receiver == predicateObject)) {
      CompilerAsserts.neverPartOfCompilation("AbstractIfMessageNode");
      return argument.getMethod().invoke(frame, call, argument);
    } else {
      return Nil.nilObject;
    }
  }

  protected final boolean isSameArgument(final Object receiver, final SBlock argument) {
    return branchMethod == null || argument.getMethod() == branchMethod;
  }
}
