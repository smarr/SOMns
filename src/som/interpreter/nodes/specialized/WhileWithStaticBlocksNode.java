package som.interpreter.nodes.specialized;

import som.interpreter.nodes.literals.BlockNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SObject;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class WhileWithStaticBlocksNode extends AbstractWhileNode {
  @Child protected BlockNode receiver;
  @Child protected BlockNode argument;

  private WhileWithStaticBlocksNode(final BlockNode receiver,
      final BlockNode argument, final SBlock rcvr, final SBlock arg,
      final boolean predicateBool, final Universe universe, final SourceSection source) {
    super(rcvr, arg, predicateBool, universe, source);
    this.receiver = receiver;
    this.argument = argument;
  }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    SBlock rcvr = receiver.executeSBlock(frame);
    SBlock arg  = argument.executeSBlock(frame);
    return executeEvaluated(frame, rcvr, arg);
  }

  @Override
  public final void executeVoid(final VirtualFrame frame) {
    SBlock rcvr = receiver.executeSBlock(frame);
    SBlock arg  = argument.executeSBlock(frame);
    executeEvaluatedVoid(frame, rcvr, arg);
  }

  @Override
  protected final SObject doWhileConditionally(final VirtualFrame frame,
      final SBlock loopCondition,
      final SBlock loopBody) {
    return doWhileUnconditionally(frame, loopCondition, loopBody);
  }

  public static final class WhileTrueStaticBlocksNode extends WhileWithStaticBlocksNode {
    public WhileTrueStaticBlocksNode(final BlockNode receiver,
        final BlockNode argument, final SBlock rcvr, final SBlock arg,
        final Universe universe, final SourceSection source) {
      super(receiver, argument, rcvr, arg, true, universe, source);
    }
  }

  public static final class WhileFalseStaticBlocksNode extends WhileWithStaticBlocksNode {
    public WhileFalseStaticBlocksNode(final BlockNode receiver,
        final BlockNode argument, final SBlock rcvr, final SBlock arg,
        final Universe universe, final SourceSection source) {
      super(receiver, argument, rcvr, arg, false, universe, source);
    }
  }
}
