package som.interpreter.nodes.specialized.whileloops;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vmobjects.SBlock;
import som.vmobjects.SObjectWithClass;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


@GenerateNodeFactory
public abstract class WhilePrimitiveNode extends BinaryExpressionNode {
  final boolean predicateBool;
  @Child protected WhileCache whileNode;

  protected WhilePrimitiveNode(final boolean predicateBool) {
    super(null);
    this.predicateBool = predicateBool;
    this.whileNode = WhileCacheNodeGen.create(predicateBool, null, null);
  }

  protected WhilePrimitiveNode(final WhilePrimitiveNode node) {
    this(node.predicateBool);
  }

  @Specialization
  protected SObjectWithClass doWhileConditionally(final VirtualFrame frame,
      final SBlock loopCondition, final SBlock loopBody) {
    return (SObjectWithClass) whileNode.executeEvaluated(frame, loopCondition, loopBody);
  }

  public abstract static class WhileTruePrimitiveNode extends WhilePrimitiveNode {
    public WhileTruePrimitiveNode() { super(true); }
  }

  public abstract static class WhileFalsePrimitiveNode extends WhilePrimitiveNode {
    public WhileFalsePrimitiveNode() { super(false); }
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
