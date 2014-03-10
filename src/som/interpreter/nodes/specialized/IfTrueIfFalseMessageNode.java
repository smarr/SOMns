package som.interpreter.nodes.specialized;

import som.interpreter.SArguments;
import som.interpreter.nodes.PreevaluatedExpression;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;
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

  private final SMethod trueMethod;
  private final SMethod falseMethod;

  @Child protected CallNode trueValueSend;
  @Child protected CallNode falseValueSend;

  private final Universe universe;

  public IfTrueIfFalseMessageNode(final Object rcvr, final Object arg1,
      final Object arg2, final Universe universe) {
    if (arg1 instanceof SBlock) {
      SBlock trueBlock = (SBlock) arg1;
      trueMethod = trueBlock.getMethod();
      trueValueSend = adoptChild(Truffle.getRuntime().createCallNode(
          trueMethod.getCallTarget()));
    } else {
      trueMethod = null;
    }

    if (arg2 instanceof SBlock) {
      SBlock falseBlock = (SBlock) arg2;
      falseMethod = falseBlock.getMethod();
      falseValueSend = adoptChild(Truffle.getRuntime().createCallNode(
          falseMethod.getCallTarget()));
    } else {
      falseMethod = null;
    }

    this.universe = universe;
  }

  public IfTrueIfFalseMessageNode(final IfTrueIfFalseMessageNode node) {
    trueMethod = node.trueMethod;
    if (node.trueMethod != null) {
      trueValueSend = adoptChild(Truffle.getRuntime().createCallNode(
          trueMethod.getCallTarget()));
    }

    falseMethod = node.falseMethod;
    if (node.falseMethod != null) {
      falseValueSend = adoptChild(Truffle.getRuntime().createCallNode(
          falseMethod.getCallTarget()));
    }
    this.universe = node.universe;
  }

  @Override
  public final Object executePreEvaluated(final VirtualFrame frame,
      final Object receiver, final Object[] arguments) {
    return executeEvaluated(frame, receiver, arguments[0], arguments[1]);
  }

  protected final boolean hasSameArguments(final Object receiver, final Object firstArg, final Object secondArg) {
    return (trueMethod  == null || ((SBlock) firstArg).getMethod()  == trueMethod)
        && (falseMethod == null || ((SBlock) secondArg).getMethod() == falseMethod);
  }

  @Specialization(order = 1, guards = "hasSameArguments")
  public Object doIfTrueIfFalseWithInlining(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final SBlock falseBlock) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      return trueValueSend.call(frame.pack(),
          new SArguments(trueBlock, new Object[0]));
    } else {
      assert receiver == universe.falseObject;
      ifFalseBranch.enter();
      return falseValueSend.call(frame.pack(),
          new SArguments(falseBlock, new Object[0]));
    }
  }

  @Specialization(order = 10)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final SBlock falseBlock) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      return trueBlock.getMethod().invoke(frame.pack(), trueBlock, universe);
    } else {
      assert receiver == universe.falseObject;
      ifFalseBranch.enter();
      return falseBlock.getMethod().invoke(frame.pack(), falseBlock, universe);
    }
  }

  @Specialization(order = 18, guards = "hasSameArguments")
  public Object doIfTrueIfFalseWithInlining(final VirtualFrame frame,
      final SObject receiver, final Object trueValue, final SBlock falseBlock) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      return trueValue;
    } else {
      assert receiver == universe.falseObject;
      ifFalseBranch.enter();
      return falseValueSend.call(frame.pack(), new SArguments(falseBlock,
          new Object[0]));
    }
  }

  @Specialization(order = 19, guards = "hasSameArguments")
  public Object doIfTrueIfFalseWithInlining(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final Object falseValue) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      return trueValueSend.call(frame.pack(), new SArguments(trueBlock,
          new Object[0]));
    } else {
      ifFalseBranch.enter();
      assert receiver == universe.falseObject;
      return falseValue;
    }
  }

  @Specialization(order = 20)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final Object trueValue, final SBlock falseBlock) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      return trueValue;
    } else {
      assert receiver == universe.falseObject;
      ifFalseBranch.enter();
      return falseBlock.getMethod().invoke(frame.pack(), falseBlock, universe);
    }
  }

  @Specialization(order = 30)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final Object falseValue) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      return trueBlock.getMethod().invoke(frame.pack(), trueBlock, universe);
    } else {
      ifFalseBranch.enter();
      assert receiver == universe.falseObject;
      return falseValue;
    }
  }

  @Specialization(order = 40)
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
