package som.interpreter.nodes.specialized;

import som.interpreter.nodes.PreevaluatedExpression;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.CallNode;
import com.oracle.truffle.api.utilities.BranchProfile;

public abstract class IfTrueIfFalseMessageNode extends TernaryExpressionNode
    implements PreevaluatedExpression {
  private final BranchProfile ifFalseBranch = new BranchProfile();
  private final BranchProfile ifTrueBranch  = new BranchProfile();

  private final SInvokable trueMethod;
  private final SInvokable falseMethod;

  @Child protected CallNode trueValueSend;
  @Child protected CallNode falseValueSend;

  private final Universe universe;

  public IfTrueIfFalseMessageNode(final Object rcvr, final Object arg1,
      final Object arg2, final Universe universe) {
    if (arg1 instanceof SBlock) {
      SBlock trueBlock = (SBlock) arg1;
      trueMethod = trueBlock.getMethod();
      trueValueSend = Truffle.getRuntime().createCallNode(
          trueMethod.getCallTarget());
    } else {
      trueMethod = null;
    }

    if (arg2 instanceof SBlock) {
      SBlock falseBlock = (SBlock) arg2;
      falseMethod = falseBlock.getMethod();
      falseValueSend = Truffle.getRuntime().createCallNode(
          falseMethod.getCallTarget());
    } else {
      falseMethod = null;
    }

    this.universe = universe;
  }

  public IfTrueIfFalseMessageNode(final IfTrueIfFalseMessageNode node) {
    trueMethod = node.trueMethod;
    if (node.trueMethod != null) {
      trueValueSend = Truffle.getRuntime().createCallNode(
          trueMethod.getCallTarget());
    }

    falseMethod = node.falseMethod;
    if (node.falseMethod != null) {
      falseValueSend = Truffle.getRuntime().createCallNode(
          falseMethod.getCallTarget());
    }
    this.universe = node.universe;
  }

  @Override
  public final Object executePreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return executeEvaluated(frame, arguments[0], arguments[1], arguments[2]);
  }

  protected final boolean hasSameArguments(final Object receiver, final Object firstArg, final Object secondArg) {
    return (trueMethod  == null || ((SBlock) firstArg).getMethod()  == trueMethod)
        && (falseMethod == null || ((SBlock) secondArg).getMethod() == falseMethod);
  }

  @Specialization(order = 1, guards = "hasSameArguments")
  public final Object doIfTrueIfFalseWithInlining(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final SBlock falseBlock) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      return trueValueSend.call(frame, new Object[] {trueBlock});
    } else {
      assert receiver == universe.falseObject;
      ifFalseBranch.enter();
      return falseValueSend.call(frame, new Object[] {falseBlock});
    }
  }

  @Specialization(order = 10)
  public final Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final SBlock falseBlock) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      return trueBlock.getMethod().invoke(trueBlock);
    } else {
      assert receiver == universe.falseObject;
      ifFalseBranch.enter();
      return falseBlock.getMethod().invoke(falseBlock);
    }
  }

  @Specialization(order = 18, guards = "hasSameArguments")
  public final Object doIfTrueIfFalseWithInlining(final VirtualFrame frame,
      final SObject receiver, final Object trueValue, final SBlock falseBlock) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      return trueValue;
    } else {
      assert receiver == universe.falseObject;
      ifFalseBranch.enter();
      return falseValueSend.call(frame, new Object[] {falseBlock});
    }
  }

  @Specialization(order = 19, guards = "hasSameArguments")
  public final Object doIfTrueIfFalseWithInlining(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final Object falseValue) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      return trueValueSend.call(frame, new Object[] {trueBlock});
    } else {
      ifFalseBranch.enter();
      assert receiver == universe.falseObject;
      return falseValue;
    }
  }

  @Specialization(order = 20)
  public final Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final Object trueValue, final SBlock falseBlock) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      return trueValue;
    } else {
      assert receiver == universe.falseObject;
      ifFalseBranch.enter();
      return falseBlock.getMethod().invoke(falseBlock);
    }
  }

  @Specialization(order = 30)
  public final Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final Object falseValue) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      return trueBlock.getMethod().invoke(trueBlock);
    } else {
      ifFalseBranch.enter();
      assert receiver == universe.falseObject;
      return falseValue;
    }
  }

  @Specialization(order = 40)
  public final Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final Object trueValue, final Object falseValue) {
    if (receiver == universe.trueObject) {
      return trueValue;
    } else {
      assert receiver == universe.falseObject;
      return falseValue;
    }
  }
}
