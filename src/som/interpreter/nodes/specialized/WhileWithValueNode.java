package som.interpreter.nodes.specialized;

import som.interpreter.nodes.BinaryMessageNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.literals.BlockNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class WhileWithValueNode extends AbstractWhileMessageNode {
  @Child protected ExpressionNode receiver;

  private WhileWithValueNode(final BinaryMessageNode node,
      final ExpressionNode receiver, final BlockNode argument,
      final SBlock arg, final SObject predicateBool) {
    super(node, argument, arg, predicateBool);
    this.receiver = adoptChild(receiver);
  }

  @Override public final ExpressionNode getReceiver() { return receiver; }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    Object rcvr = receiver.executeGeneric(frame);
    SBlock arg  = argument.executeSBlock(frame);
    return executeEvaluated(frame, rcvr, arg);
  }

  @Override
  public final Object executeEvaluated(final VirtualFrame frame, final Object rcvr, final Object arg) {
    return doWhileUnconditionally(frame, rcvr, (SBlock) arg);
  }

  protected SObject doWhileUnconditionally(final VirtualFrame frame,
      final Object loopConditionValue, final SBlock loopBody) {
    int iterationCount = 0;

    if (loopConditionValue != predicateBool) {
      return universe.nilObject;
    }

    try {
      while (true) {
        bodyValueSend.executeEvaluated(frame, universe.newBlock(loopBody));

        if (CompilerDirectives.inInterpreter()) {
          iterationCount =+ 10;
        }
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        reportLoopCount(iterationCount);
      }
    }
  }

  public static final class WhileTrueValueNode extends WhileWithValueNode {
    public WhileTrueValueNode(final BinaryMessageNode node,
        final ExpressionNode receiver, final BlockNode argument, final SBlock arg) {
      super(node, receiver, argument, arg, Universe.current().trueObject);
    }
  }

  public static final class WhileFalseValueNode extends WhileWithValueNode {
    public WhileFalseValueNode(final BinaryMessageNode node,
        final ExpressionNode receiver, final BlockNode argument, final SBlock arg) {
      super(node, receiver, argument, arg, Universe.current().falseObject);
    }
  }
}
