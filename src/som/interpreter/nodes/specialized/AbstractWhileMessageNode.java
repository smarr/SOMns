package som.interpreter.nodes.specialized;

import som.interpreter.BlockHelper;
import som.interpreter.nodes.BinaryMessageNode;
import som.interpreter.nodes.UnaryMessageNode;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.LoopCountReceiver;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


public abstract class AbstractWhileMessageNode extends BinaryMessageNode {
  private final SMethod conditionMethod;
  private final SMethod bodyMethod;

  @Child protected UnaryMessageNode conditionValueSend;
  @Child protected UnaryMessageNode bodyValueSend;

  public AbstractWhileMessageNode(final BinaryMessageNode node,
      final Object rcvr, final Object arg) {
    super(node);
    if (rcvr instanceof SBlock) {
      SBlock rcvrBlock = (SBlock) rcvr;
      conditionMethod = rcvrBlock.getMethod();
      conditionValueSend = adoptChild(BlockHelper.createInlineableNode(conditionMethod, universe));
    } else {
      conditionMethod = null;
    }

    if (arg instanceof SBlock) {
      SBlock argBlock = (SBlock) arg;
      bodyMethod = argBlock.getMethod();
      bodyValueSend = adoptChild(BlockHelper.createInlineableNode(bodyMethod, universe));
    } else {
      bodyMethod = null;
    }
  }

  public AbstractWhileMessageNode(final AbstractWhileMessageNode node) {
    super(node);
    conditionMethod = node.conditionMethod;
    if (node.conditionMethod != null) {
      conditionValueSend = adoptChild(BlockHelper.createInlineableNode(conditionMethod, universe));
    }

    bodyMethod = node.bodyMethod;
    if (node.bodyMethod != null) {
      bodyValueSend = adoptChild(BlockHelper.createInlineableNode(bodyMethod, universe));
    }
  }

  protected SObject doWhile(final VirtualFrame frame,
      final SBlock loopCondition, final SBlock loopBody, final SObject continueBool) {
    int iterationCount = 0;

    Object loopConditionResult = conditionValueSend.executeEvaluated(frame, BlockHelper.createBlock(loopCondition, universe));

    try {
      // TODO: this is a simplification, we don't cover the case receiver isn't a boolean
      while (loopConditionResult == continueBool) {
        bodyValueSend.executeEvaluated(frame, BlockHelper.createBlock(loopBody, universe));
        loopConditionResult = conditionValueSend.executeEvaluated(frame, BlockHelper.createBlock(loopCondition, universe));

        if (CompilerDirectives.inInterpreter()) {
          iterationCount++;
        }
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        reportLoopCount(iterationCount);
      }
    }

    return universe.nilObject;
  }

  protected SObject doWhile(final VirtualFrame frame,
      final Object loopCondition, final SBlock loopBody) {
    int iterationCount = 0;

    try {
      while (true) {
        bodyValueSend.executeEvaluated(frame, BlockHelper.createBlock(loopBody, universe));

        if (CompilerDirectives.inInterpreter()) {
          iterationCount++;
        }
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        reportLoopCount(iterationCount);
      }
    }
  }

  private void reportLoopCount(final int count) {
    CompilerAsserts.neverPartOfCompilation();
    Node current = getParent();
    while (current != null && !(current instanceof RootNode)) {
      current = current.getParent();
    }
    if (current != null) {
      RootNode root = (RootNode) current;
      if (root.getCallTarget() instanceof LoopCountReceiver) {
        ((LoopCountReceiver) root.getCallTarget()).reportLoopCount(count);
      }
    }
  }

  protected boolean receiverIsTrueObject(final Object receiver) {
    return receiver == universe.trueObject;
  }

  protected boolean receiverIsFalseObject(final Object receiver) {
    return receiver == universe.falseObject;
  }

  protected boolean isSameArgument(final Object receiver, final Object argument) {
    return (this.conditionMethod == null || ((SBlock) receiver).getMethod() == conditionMethod)
        && (this.bodyMethod      == null || ((SBlock) argument).getMethod() == bodyMethod);
  }
}
