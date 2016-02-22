package som.interpreter.nodes.specialized.whileloops;

import som.compiler.Tags;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.specialized.SomLoop;
import som.vm.constants.Nil;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;

import dym.Tagging;


public final class WhileInlinedLiteralsNode extends ExpressionNode {

  @Child private ExpressionNode conditionNode;
  @Child private ExpressionNode bodyNode;

  private final boolean expectedBool;

  @SuppressWarnings("unused") private final ExpressionNode conditionActualNode;
  @SuppressWarnings("unused") private final ExpressionNode bodyActualNode;

  public WhileInlinedLiteralsNode(
      final ExpressionNode inlinedConditionNode,
      final ExpressionNode inlinedBodyNode,
      final boolean expectedBool,
      final ExpressionNode originalConditionNode,
      final ExpressionNode originalBodyNode,
      final SourceSection sourceSection) {
    super(Tagging.cloneAndAddTags(sourceSection, Tags.LOOP_BODY));
    this.conditionNode = inlinedConditionNode;
    this.bodyNode      = inlinedBodyNode;
    this.expectedBool  = expectedBool;
    this.conditionActualNode = originalConditionNode;
    this.bodyActualNode      = originalBodyNode;
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

        if (CompilerDirectives.inInterpreter()) { iterationCount++; }
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
