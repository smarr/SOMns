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

  protected WhilePrimitiveNode(final boolean executesEnforced, final boolean predicateBool) {
    super(null, executesEnforced);
    this.predicateBool = predicateBool;
    this.whileNode = WhileCache.create(predicateBool, executesEnforced);
  }

  protected WhilePrimitiveNode(final WhilePrimitiveNode node) {
    this(node.executesEnforced, node.predicateBool);
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
    public WhileTruePrimitiveNode(final boolean executesEnforced) { super(executesEnforced, true); }
    public WhileTruePrimitiveNode(final WhileTruePrimitiveNode node) { this(node.executesEnforced); }
  }

  public abstract static class WhileFalsePrimitiveNode extends WhilePrimitiveNode {
    public WhileFalsePrimitiveNode(final boolean executesEnforced) { super(executesEnforced, false); }
    public WhileFalsePrimitiveNode(final WhileFalsePrimitiveNode node) { this(node.executesEnforced); }
  }
}
