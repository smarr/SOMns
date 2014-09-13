package som.interpreter.nodes.specialized;

import som.interpreter.SArguments;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.utilities.BranchProfile;

public abstract class IfTrueIfFalseMessageNode extends TernaryExpressionNode {
  private final BranchProfile ifFalseBranch = new BranchProfile();
  private final BranchProfile ifTrueBranch  = new BranchProfile();

  private final SInvokable trueMethod;
  private final SInvokable falseMethod;

  @Child protected DirectCallNode trueValueSend;
  @Child protected DirectCallNode falseValueSend;

  @Child private IndirectCallNode call;

  private final boolean trueEnforced;
  private final boolean falseEnforced;

  public IfTrueIfFalseMessageNode(final Object rcvr, final Object arg1,
      final Object arg2, final boolean executesEnforced) {
    super(executesEnforced);

    if (arg1 instanceof SBlock) {
      SBlock trueBlock = (SBlock) arg1;
      trueMethod = trueBlock.getMethod();
      trueValueSend = Truffle.getRuntime().createDirectCallNode(
          trueMethod.getCallTarget(trueBlock.isEnforced() || executesEnforced));
      trueEnforced = trueBlock.isEnforced();
    } else {
      trueMethod = null;
      trueEnforced = false; // TODO:does this even matter?
    }

    if (arg2 instanceof SBlock) {
      SBlock falseBlock = (SBlock) arg2;
      falseMethod = falseBlock.getMethod();
      falseValueSend = Truffle.getRuntime().createDirectCallNode(
          falseMethod.getCallTarget(falseBlock.isEnforced() || executesEnforced));
      falseEnforced = falseBlock.isEnforced();
    } else {
      falseMethod = null;
      falseEnforced = false; // TODO: does this even matter?
    }

    call = Truffle.getRuntime().createIndirectCallNode();
  }

  public IfTrueIfFalseMessageNode(final IfTrueIfFalseMessageNode node) {
    super(node.executesEnforced);
    trueMethod = node.trueMethod;
    trueEnforced = node.trueEnforced;
    if (node.trueMethod != null) {
      trueValueSend = Truffle.getRuntime().createDirectCallNode(
          trueMethod.getCallTarget(trueEnforced || executesEnforced));

    }

    falseMethod = node.falseMethod;
    falseEnforced = node.falseEnforced;
    if (node.falseMethod != null) {
      falseValueSend = Truffle.getRuntime().createDirectCallNode(
          falseMethod.getCallTarget(falseEnforced || executesEnforced));
    }
    call = Truffle.getRuntime().createIndirectCallNode();
  }

  protected final boolean hasSameArguments(final Object receiver, final Object firstArg, final Object secondArg) {
    return (trueMethod  == null || ((SBlock) firstArg).getMethod()  == trueMethod)
        && (falseMethod == null || ((SBlock) secondArg).getMethod() == falseMethod);
  }

  @Specialization(order = 1, guards = "hasSameArguments")
  public final Object doIfTrueIfFalseWithInlining(final VirtualFrame frame,
      final boolean receiver, final SBlock trueBlock, final SBlock falseBlock) {
    SObject domain = SArguments.domain(frame);

    if (receiver) {
      ifTrueBranch.enter();
      return trueValueSend.call(frame, SArguments.createSArguments(domain, trueEnforced, new Object[] {trueBlock}));
    } else {
      ifFalseBranch.enter();
      return falseValueSend.call(frame, SArguments.createSArguments(domain, falseEnforced, new Object[] {falseBlock}));
    }
  }

  @Specialization(order = 10)
  public final Object doIfTrueIfFalse(final VirtualFrame frame,
      final boolean receiver, final SBlock trueBlock, final SBlock falseBlock) {
    SObject domain = SArguments.domain(frame);
    CompilerAsserts.neverPartOfCompilation("IfTrueIfFalseMessageNode.10");
    if (receiver) {
      ifTrueBranch.enter();
      return trueBlock.getMethod().invoke(frame, call, domain, trueEnforced, trueBlock);
    } else {
      ifFalseBranch.enter();
      return falseBlock.getMethod().invoke(frame, call, domain, falseEnforced, falseBlock);
    }
  }

  @Specialization(order = 18, guards = "hasSameArguments")
  public final Object doIfTrueIfFalseWithInlining(final VirtualFrame frame,
      final boolean receiver, final Object trueValue, final SBlock falseBlock) {
    if (receiver) {
      ifTrueBranch.enter();
      return trueValue;
    } else {
      ifFalseBranch.enter();
      SObject domain = SArguments.domain(frame);
      return falseValueSend.call(frame, SArguments.createSArguments(domain, falseEnforced, new Object[] {falseBlock}));
    }
  }

  @Specialization(order = 19, guards = "hasSameArguments")
  public final Object doIfTrueIfFalseWithInlining(final VirtualFrame frame,
      final boolean receiver, final SBlock trueBlock, final Object falseValue) {
    if (receiver) {
      ifTrueBranch.enter();
      SObject domain = SArguments.domain(frame);
      return trueValueSend.call(frame, SArguments.createSArguments(domain, trueEnforced, new Object[] {trueBlock}));
    } else {
      ifFalseBranch.enter();
      return falseValue;
    }
  }

  @Specialization(order = 20)
  public final Object doIfTrueIfFalse(final VirtualFrame frame,
      final boolean receiver, final Object trueValue, final SBlock falseBlock) {
    if (receiver) {
      ifTrueBranch.enter();
      return trueValue;
    } else {
      ifFalseBranch.enter();
      CompilerAsserts.neverPartOfCompilation("IfTrueIfFalseMessageNode.20");
      SObject domain   = SArguments.domain(frame);
      boolean enforced = SArguments.enforced(frame);
      return falseBlock.getMethod().invoke(frame, call, domain, enforced, falseBlock);
    }
  }

  @Specialization(order = 30)
  public final Object doIfTrueIfFalse(final VirtualFrame frame,
      final boolean receiver, final SBlock trueBlock, final Object falseValue) {
    if (receiver) {
      ifTrueBranch.enter();
      CompilerAsserts.neverPartOfCompilation("IfTrueIfFalseMessageNode.30");
      SObject domain   = SArguments.domain(frame);
      boolean enforced = SArguments.enforced(frame);
      return trueBlock.getMethod().invoke(frame, call, domain, enforced, trueBlock);
    } else {
      ifFalseBranch.enter();
      return falseValue;
    }
  }

  @Specialization(order = 40)
  public final Object doIfTrueIfFalse(final VirtualFrame frame,
      final boolean receiver, final Object trueValue, final Object falseValue) {
    if (receiver) {
      return trueValue;
    } else {
      return falseValue;
    }
  }
}
