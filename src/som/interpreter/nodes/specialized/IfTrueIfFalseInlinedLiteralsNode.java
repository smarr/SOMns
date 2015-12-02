package som.interpreter.nodes.specialized;

import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.ConditionProfile;


/**
 * This is a very specialized node that is used when we got two literal blocks
 * and replace the normal message sends by a completely inlined version.
 *
 * Note, it is also applicable if one of the argument expressions is a proper
 * literal of some sort.
 */
public final class IfTrueIfFalseInlinedLiteralsNode extends ExpressionNode {
  private final ConditionProfile condProf = ConditionProfile.createCountingProfile();

  @Child private ExpressionNode conditionNode;
  @Child private ExpressionNode trueNode;
  @Child private ExpressionNode falseNode;

  // In case we need to revert from this optimistic optimization, keep the
  // original nodes around
  private final ExpressionNode trueActualNode;
  private final ExpressionNode falseActualNode;

  public IfTrueIfFalseInlinedLiteralsNode(
      final ExpressionNode conditionNode,
      final ExpressionNode inlinedTrueNode,
      final ExpressionNode inlinedFalseNode,
      final ExpressionNode originalTrueNode,
      final ExpressionNode originalFalseNode,
      final SourceSection sourceSection) {
    super(sourceSection);
    this.conditionNode   = conditionNode;
    this.trueNode        = inlinedTrueNode;
    this.falseNode       = inlinedFalseNode;
    this.trueActualNode  = originalTrueNode;
    this.falseActualNode = originalFalseNode;
  }

  private boolean evaluateCondition(final VirtualFrame frame) {
    try {
      return condProf.profile(conditionNode.executeBoolean(frame));
    } catch (UnexpectedResultException e) {
      // TODO: should rewrite to a node that does a proper message send...
      throw new UnsupportedSpecializationException(this,
          new Node[] {conditionNode}, e.getResult());
    }
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    if (evaluateCondition(frame)) {
      return trueNode.executeGeneric(frame);
    } else {
      return falseNode.executeGeneric(frame);
    }
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    Node parent = getParent();
    if (parent instanceof ExpressionNode) {
      return ((ExpressionNode) parent).isResultUsed(this);
    }
    return true;
  }
}
