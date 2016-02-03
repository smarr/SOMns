package som.interpreter.nodes.specialized.whileloops;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.specialized.SomLoop;
import som.vm.constants.Nil;
import som.vmobjects.SBlock;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;


public abstract class AbstractWhileNode extends BinaryExpressionNode {
  @Child protected DirectCallNode conditionValueSend;
  @Child protected DirectCallNode bodyValueSend;

  protected final boolean predicateBool;

  public AbstractWhileNode(final SBlock rcvr, final SBlock arg,
      final boolean predicateBool, final SourceSection source) {
    super(source);

    CallTarget callTargetCondition = rcvr.getMethod().getCallTarget();
    conditionValueSend = Truffle.getRuntime().createDirectCallNode(
        callTargetCondition);

    CallTarget callTargetBody = arg.getMethod().getCallTarget();
    bodyValueSend = Truffle.getRuntime().createDirectCallNode(
        callTargetBody);

    this.predicateBool = predicateBool;
  }

  @Override
  public final Object executeEvaluated(final VirtualFrame frame,
      final Object rcvr, final Object arg) {
    return doWhileConditionally(frame, (SBlock) rcvr, (SBlock) arg);
  }

  protected final Object doWhileUnconditionally(final VirtualFrame frame,
      final SBlock loopCondition, final SBlock loopBody) {
    long iterationCount = 0;

    boolean loopConditionResult = (boolean) conditionValueSend.call(
        frame, new Object[] {loopCondition});

    try {
      // TODO: this is a simplification, we don't cover the case receiver isn't a boolean
      while (loopConditionResult == predicateBool) {
        bodyValueSend.call(frame, new Object[] {loopBody});
        loopConditionResult = (boolean) conditionValueSend.call(
            frame, new Object[] {loopCondition});

        if (CompilerDirectives.inInterpreter()) { iterationCount++; }
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(iterationCount, this);
      }
    }
    return Nil.nilObject;
  }

  protected abstract Object doWhileConditionally(final VirtualFrame frame,
      final SBlock loopCondition, final SBlock loopBody);

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
