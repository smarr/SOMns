package som.interpreter.nodes.specialized.whileloops;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import bd.inlining.Inline;
import bd.inlining.Inline.False;
import bd.inlining.Inline.True;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.interpreter.nodes.specialized.SomLoop;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.vm.constants.Nil;
import tools.dym.Tags.LoopNode;


@Inline(selector = "whileTrue:", inlineableArgIdx = {0, 1}, additionalArgs = {True.class})
@Inline(selector = "whileFalse:", inlineableArgIdx = {0, 1}, additionalArgs = {False.class})
public final class WhileInlinedLiteralsNode extends ExprWithTagsNode {

  @Child private ExpressionNode conditionNode;
  @Child private ExpressionNode bodyNode;

  private final boolean expectedBool;

  @SuppressWarnings("unused") private final ExpressionNode conditionActualNode;
  @SuppressWarnings("unused") private final ExpressionNode bodyActualNode;

  public WhileInlinedLiteralsNode(final ExpressionNode originalConditionNode,
      final ExpressionNode originalBodyNode, final ExpressionNode inlinedConditionNode,
      final ExpressionNode inlinedBodyNode, final boolean expectedBool) {
    this.conditionNode = inlinedConditionNode;
    this.bodyNode = inlinedBodyNode;
    this.expectedBool = expectedBool;
    this.conditionActualNode = originalConditionNode;
    this.bodyActualNode = originalBodyNode;

    inlinedConditionNode.markAsControlFlowCondition();
    inlinedBodyNode.markAsLoopBody();
  }

  @Override
  public boolean hasTag(final Class<? extends Tag> tag) {
    if (tag == LoopNode.class) {
      return true;
    } else {
      return super.hasTag(tag);
    }
  }

  private boolean evaluateCondition(final VirtualFrame frame) {
    try {
      return conditionNode.executeBoolean(frame);
    } catch (UnexpectedResultException e) {
      // TODO: should rewrite to a node that does a proper message send...
      throw new UnsupportedSpecializationException(this,
          new Node[] {conditionNode}, e.getResult());
    }
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    long iterationCount = 0;

    // TODO: this is a simplification, we don't cover the case receiver isn't a boolean
    boolean loopConditionResult = evaluateCondition(frame);

    try {
      while (loopConditionResult == expectedBool) {
        bodyNode.executeGeneric(frame);
        loopConditionResult = evaluateCondition(frame);

        if (CompilerDirectives.inInterpreter()) {
          iterationCount++;
        }
        ObjectTransitionSafepoint.INSTANCE.checkAndPerformSafepoint();
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(iterationCount, this);
      }
    }
    return Nil.nilObject;
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
