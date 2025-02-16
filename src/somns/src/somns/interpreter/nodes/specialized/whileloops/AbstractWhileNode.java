package somns.interpreter.nodes.specialized.whileloops;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.DirectCallNode;

import somns.interpreter.nodes.ExpressionNode;
import somns.interpreter.nodes.nary.BinaryComplexOperation;
import somns.interpreter.nodes.specialized.SomLoop;
import somns.interpreter.objectstorage.ObjectTransitionSafepoint;
import somns.vm.constants.Nil;
import somns.vmobjects.SBlock;
import tools.dym.Tags.LoopNode;


public abstract class AbstractWhileNode extends BinaryComplexOperation {
  @Child protected DirectCallNode conditionValueSend;
  @Child protected DirectCallNode bodyValueSend;

  protected final boolean predicateBool;

  public AbstractWhileNode(final SBlock rcvr, final SBlock arg, final boolean predicateBool) {
    CallTarget callTargetCondition = rcvr.getMethod().getCallTarget();
    conditionValueSend = Truffle.getRuntime().createDirectCallNode(
        callTargetCondition);

    CallTarget callTargetBody = arg.getMethod().getCallTarget();
    bodyValueSend = Truffle.getRuntime().createDirectCallNode(
        callTargetBody);

    this.predicateBool = predicateBool;
  }

  @Override
  protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
    if (tag == LoopNode.class) {
      return true;
    } else {
      return super.hasTagIgnoringEagerness(tag);
    }
  }

  @Override
  public final Object executeEvaluated(final VirtualFrame frame,
      final Object rcvr, final Object arg) {
    return doWhileConditionally((SBlock) rcvr, (SBlock) arg);
  }

  protected final Object doWhileUnconditionally(final SBlock loopCondition,
      final SBlock loopBody) {
    long iterationCount = 0;

    boolean loopConditionResult = (boolean) conditionValueSend.call(
        new Object[] {loopCondition});

    try {
      // TODO: this is a simplification, we don't cover the case receiver isn't a boolean
      while (loopConditionResult == predicateBool) {
        bodyValueSend.call(new Object[] {loopBody});
        loopConditionResult = (boolean) conditionValueSend.call(
            new Object[] {loopCondition});

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

  protected abstract Object doWhileConditionally(SBlock loopCondition,
      SBlock loopBody);

  @Override
  @Idempotent
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
