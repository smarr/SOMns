package som.interpreter.nodes.specialized;

import som.interpreter.BlockHelper;
import static som.interpreter.BlockHelper.createInlineableNode;
import som.interpreter.nodes.TernaryMessageNode;
import som.interpreter.nodes.UnaryMessageNode;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.utilities.BranchProfile;

public abstract class IfTrueIfFalseMessageNode extends TernaryMessageNode {
  private final BranchProfile ifFalseBranch = new BranchProfile();
  private final BranchProfile ifTrueBranch  = new BranchProfile();

  private final SMethod trueMethod;
  private final SMethod falseMethod;

  @Child protected UnaryMessageNode trueValueSend;
  @Child protected UnaryMessageNode falseValueSend;

  public IfTrueIfFalseMessageNode(final TernaryMessageNode node, final Object rcvr,
      final Object arg1, final Object arg2) {
    super(node);
    if (arg1 instanceof SBlock) {
      SBlock trueBlock = (SBlock) arg1;
      trueMethod = trueBlock.getMethod();
      trueValueSend = adoptChild(createInlineableNode(trueMethod, universe));
    } else {
      trueMethod = null;
    }

    if (arg2 instanceof SBlock) {
      SBlock falseBlock = (SBlock) arg2;
      falseMethod = falseBlock.getMethod();
      falseValueSend = adoptChild(createInlineableNode(falseMethod, universe));
    } else {
      falseMethod = null;
    }
  }
  public IfTrueIfFalseMessageNode(final IfTrueIfFalseMessageNode node) {
    super(node);
    trueMethod = node.trueMethod;
    if (node.trueMethod != null) {
      trueValueSend = adoptChild(createInlineableNode(trueMethod, universe));
    }

    falseMethod = node.falseMethod;
    if (node.falseMethod != null) {
      falseValueSend = adoptChild(createInlineableNode(falseMethod, universe));
    }
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
      return trueValueSend.executeEvaluated(frame, BlockHelper.createBlock(trueBlock, universe));
    } else {
      assert receiver == universe.falseObject;
      ifFalseBranch.enter();
      return falseValueSend.executeEvaluated(frame, BlockHelper.createBlock(falseBlock, universe));
    }
  }

  @Specialization(order = 10)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final SBlock falseBlock) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      return trueBlock.getMethod().invoke(frame.pack(), BlockHelper.createBlock(trueBlock, universe), universe);
    } else {
      assert receiver == universe.falseObject;
      ifFalseBranch.enter();
      return falseBlock.getMethod().invoke(frame.pack(), BlockHelper.createBlock(falseBlock, universe), universe);
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
      return falseValueSend.executeEvaluated(frame, BlockHelper.createBlock(falseBlock, universe));
    }
  }

  @Specialization(order = 19, guards = "hasSameArguments")
  public Object doIfTrueIfFalseWithInlining(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final Object falseValue) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      return trueValueSend.executeEvaluated(frame, BlockHelper.createBlock(trueBlock, universe));
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
      return falseBlock.getMethod().invoke(frame.pack(), BlockHelper.createBlock(falseBlock, universe), universe);
    }
  }

  @Specialization(order = 30)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final Object falseValue) {
    if (receiver == universe.trueObject) {
      ifTrueBranch.enter();
      return trueBlock.getMethod().invoke(frame.pack(), BlockHelper.createBlock(trueBlock, universe), universe);
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
