package som.interpreter.nodes.specialized.whileloops;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.interpreter.nodes.specialized.SomLoop;
import som.interpreter.nodes.superinstructions.WhileSmallerEqualThanArgumentNode;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.vm.VmSettings;
import som.vm.constants.Nil;
import tools.dym.Tags.LoopNode;

@ImportStatic({WhileSmallerEqualThanArgumentNode.class, VmSettings.class})
abstract public class WhileInlinedLiteralsNode extends ExprWithTagsNode {

  @Child private ExpressionNode conditionNode;
  @Child private ExpressionNode bodyNode;

  protected final boolean expectedBool;

  @SuppressWarnings("unused") private final ExpressionNode conditionActualNode;
  @SuppressWarnings("unused") private final ExpressionNode bodyActualNode;

  public WhileInlinedLiteralsNode(final ExpressionNode inlinedConditionNode,
      final ExpressionNode inlinedBodyNode, final boolean expectedBool,
      final ExpressionNode originalConditionNode, final ExpressionNode originalBodyNode) {
    this.conditionNode = inlinedConditionNode;
    this.bodyNode = inlinedBodyNode;
    this.expectedBool = expectedBool;
    this.conditionActualNode = originalConditionNode;
    this.bodyActualNode = originalBodyNode;
  }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == LoopNode.class) {
      return true;
    } else {
      return super.isTaggedWith(tag);
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

  /** Check for superinstruction ``WhileSmallerEqualThanArgumentNode`` */
  @Specialization(guards = {"SUPERINSTRUCTIONS", "isApplicable"} )
  public Object executeAndReplace(final VirtualFrame frame,
                                  @Cached("isWhileSmallerEqualThanArgumentNode(expectedBool, getConditionNode(), frame)")
                                  boolean isApplicable) {
    return WhileSmallerEqualThanArgumentNode.replaceNode(this).executeGeneric(frame);
  }

  @Specialization(replaces = {"executeAndReplace"})
  public Object execute(final VirtualFrame frame) {
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

  public ExpressionNode getConditionNode() {
    return conditionNode;
  }

  public ExpressionNode getBodyNode() {
    return bodyNode;
  }
}
