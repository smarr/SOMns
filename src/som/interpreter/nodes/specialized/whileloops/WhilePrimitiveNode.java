package som.interpreter.nodes.specialized.whileloops;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.specialized.whileloops.WhileCache.AbstractWhileDispatch;
import som.vmobjects.SBlock;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class WhilePrimitiveNode extends BinaryExpressionNode {
  final boolean predicateBool;
  @Child protected AbstractWhileDispatch whileNode;

  protected WhilePrimitiveNode(final boolean predicateBool) {
    super(null);
    this.predicateBool = predicateBool;
    this.whileNode = WhileCache.create(predicateBool);
  }

  protected WhilePrimitiveNode(final WhilePrimitiveNode node) {
    this(node.predicateBool);
  }

  @Specialization
  protected SObject doWhileConditionally(final VirtualFrame frame,
      final SBlock loopCondition, final SBlock loopBody) {
    return whileNode.executeDispatch(frame, loopCondition, loopBody);
  }

  @Override
  public void executeVoid(final VirtualFrame frame) {
    executeGeneric(frame);
  }

  public abstract static class WhileTruePrimitiveNode extends WhilePrimitiveNode {
    public WhileTruePrimitiveNode() { super(true); }
  }

  public abstract static class WhileFalsePrimitiveNode extends WhilePrimitiveNode {
    public WhileFalsePrimitiveNode() { super(false); }
  }
}
