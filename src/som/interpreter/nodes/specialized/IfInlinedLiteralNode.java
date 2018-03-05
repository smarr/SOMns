package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;

import bd.inlining.Inline;
import bd.inlining.Inline.False;
import bd.inlining.Inline.True;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.vm.constants.Nil;


@Inline(selector = "ifTrue:", inlineableArgIdx = {1}, additionalArgs = {True.class})
@Inline(selector = "ifFalse:", inlineableArgIdx = {1}, additionalArgs = {False.class})
public final class IfInlinedLiteralNode extends ExprWithTagsNode {
  private final ConditionProfile condProf = ConditionProfile.createCountingProfile();

  @Child private ExpressionNode conditionNode;
  @Child private ExpressionNode bodyNode;

  private final boolean expectedBool;

  // In case we need to revert from this optimistic optimization, keep the
  // original nodes around
  @SuppressWarnings("unused") private final ExpressionNode bodyActualNode;

  public IfInlinedLiteralNode(final ExpressionNode conditionNode,
      final ExpressionNode originalBodyNode, final ExpressionNode inlinedBodyNode,
      final boolean expectedBool) {
    this.conditionNode = conditionNode;
    this.expectedBool = expectedBool;
    this.bodyNode = inlinedBodyNode;
    this.bodyActualNode = originalBodyNode;
    conditionNode.markAsControlFlowCondition();
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
    if (evaluateCondition(frame) == expectedBool) {
      return bodyNode.executeGeneric(frame);
    } else {
      return Nil.nilObject;
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
