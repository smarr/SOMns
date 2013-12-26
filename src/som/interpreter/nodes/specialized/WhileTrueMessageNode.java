package som.interpreter.nodes.specialized;

import som.interpreter.nodes.BinaryMessageNode;
import som.vmobjects.SBlock;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class WhileTrueMessageNode extends AbstractWhileMessageNode {
  public WhileTrueMessageNode(final BinaryMessageNode node, final Object rcvr,
      final Object arg) { super(node, rcvr, arg); }
  public WhileTrueMessageNode(final WhileTrueMessageNode node) { super(node); }

  @Specialization(guards = "isSameArgument")
  public SObject doWhileTrue(final VirtualFrame frame,
      final SBlock loopCondition, final SBlock loopBody) {
    return doWhile(frame, loopCondition, loopBody, universe.trueObject);
  }

  @Specialization(guards = {"receiverIsTrueObject", "isSameArgument"})
  public SObject doWhileTrue(final VirtualFrame frame,
      final Object loopCondition, final SBlock loopBody) {
    return doWhile(frame, loopCondition, loopBody);
  }
}
