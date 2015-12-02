package som.interpreter.nodes.specialized.whileloops;

import som.interpreter.Invokable;
import som.interpreter.nodes.ExpressionNode;
import som.vm.constants.Nil;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;


public final class WhileInlinedLiteralsNode extends ExpressionNode {

  @Child private ExpressionNode conditionNode;
  @Child private ExpressionNode bodyNode;

  private final boolean expectedBool;

  private final ExpressionNode conditionActualNode;
  private final ExpressionNode bodyActualNode;

  public WhileInlinedLiteralsNode(
      final ExpressionNode inlinedConditionNode,
      final ExpressionNode inlinedBodyNode,
      final boolean expectedBool,
      final ExpressionNode originalConditionNode,
      final ExpressionNode originalBodyNode,
      final SourceSection sourceSection) {
    super(sourceSection);
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
        reportLoopCount(iterationCount);
      }
    }
    return Nil.nilObject;
  }

  protected void reportLoopCount(final long count) {
    CompilerAsserts.neverPartOfCompilation("reportLoopCount");
    Node current = getParent();
    while (current != null && !(current instanceof RootNode)) {
      current = current.getParent();
    }
    if (current != null) {
      ((Invokable) current).propagateLoopCountThroughoutMethodScope(count);
    }
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
