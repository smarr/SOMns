package som.interpreter.nodes.specialized;

import static som.interpreter.BlockHelper.createInlineableNode;
import som.interpreter.nodes.BinaryMessageNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.UnaryMessageNode;
import som.interpreter.nodes.literals.BlockNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class WhileWithStaticBlocksNode extends AbstractWhileMessageNode {
  @Child protected BlockNode receiver;

  @Child protected UnaryMessageNode conditionValueSend;

  private WhileWithStaticBlocksNode(final BinaryMessageNode node,
      final BlockNode receiver, final BlockNode argument,
      final SBlock rcvr, final SBlock arg, final SObject predicateBool) {
    super(node, argument, arg, predicateBool);
    this.receiver = adoptChild(receiver);
    conditionValueSend = adoptChild(createInlineableNode(rcvr.getMethod(), universe));
  }

  @Override public final ExpressionNode getReceiver() { return receiver; }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    SBlock rcvr = receiver.executeSBlock(frame);
    SBlock arg  = argument.executeSBlock(frame);
    return executeEvaluated(frame, rcvr, arg);
  }

  @Override
  public final Object executeEvaluated(final VirtualFrame frame, final Object rcvr, final Object arg) {
    return doWhileConditionally(frame, (SBlock) rcvr, (SBlock) arg);
  }

  protected final SObject doWhileConditionally(final VirtualFrame frame,
      final SBlock loopCondition, final SBlock loopBody) {
    int iterationCount = 0;
    Object loopConditionResult = conditionValueSend.executeEvaluated(frame, universe.newBlock(loopCondition));

    try {
      // TODO: this is a simplification, we don't cover the case receiver isn't a boolean
      while (loopConditionResult == predicateBool) {
        bodyValueSend.executeEvaluated(frame, universe.newBlock(loopBody));
        loopConditionResult = conditionValueSend.executeEvaluated(frame, universe.newBlock(loopCondition));

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

  public static final class WhileTrueStaticBlocksNode extends WhileWithStaticBlocksNode {
    public WhileTrueStaticBlocksNode(final BinaryMessageNode node,
        final BlockNode receiver, final BlockNode argument,
        final SBlock rcvr, final SBlock arg) {
      super(node, receiver, argument, rcvr, arg, Universe.current().trueObject);
    }
  }

  public static final class WhileFalseStaticBlocksNode extends WhileWithStaticBlocksNode {
    public WhileFalseStaticBlocksNode(final BinaryMessageNode node,
        final BlockNode receiver, final BlockNode argument,
        final SBlock rcvr, final SBlock arg) {
      super(node, receiver, argument, rcvr, arg, Universe.current().falseObject);
    }
  }
}
