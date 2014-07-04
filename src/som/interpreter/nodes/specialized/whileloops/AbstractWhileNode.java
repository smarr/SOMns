package som.interpreter.nodes.specialized.whileloops;

import som.interpreter.AbstractInvokable;
import som.interpreter.SArguments;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;


public abstract class AbstractWhileNode extends BinaryExpressionNode {
  @Child protected DirectCallNode conditionValueSend;
  @Child protected DirectCallNode bodyValueSend;

  private final boolean conditionEnforced;
  private final boolean bodyEnforced;

  protected final boolean predicateBool;
  protected final Universe universe;

  public AbstractWhileNode(final SBlock rcvr, final SBlock arg,
      final boolean predicateBool, final Universe universe,
      final SourceSection source, final boolean executesEnforced) {
    super(source, executesEnforced);

    CallTarget callTargetCondition = rcvr.getMethod().getCallTarget();
    conditionValueSend = Truffle.getRuntime().createDirectCallNode(
        callTargetCondition);
    conditionEnforced = rcvr.isEnforced();

    CallTarget callTargetBody = arg.getMethod().getCallTarget();
    bodyValueSend = Truffle.getRuntime().createDirectCallNode(
        callTargetBody);
    bodyEnforced = arg.isEnforced();

    this.predicateBool = predicateBool;
    this.universe = universe;
  }

  @Override
  public final Object executeEvaluated(final VirtualFrame frame,
      final Object rcvr, final Object arg) {
    return doWhileConditionally(frame, (SBlock) rcvr, (SBlock) arg);
  }

  @Override
  public final void executeEvaluatedVoid(final VirtualFrame frame,
      final Object rcvr, final Object arg) {
    doWhileConditionally(frame, (SBlock) rcvr, (SBlock) arg);
  }

  protected final SObject doWhileUnconditionally(final VirtualFrame frame,
      final SBlock loopCondition, final SBlock loopBody) {
    long iterationCount = 0;
    SObject domain = SArguments.domain(frame);
    boolean loopConditionResult = (boolean) conditionValueSend.call(
        frame, SArguments.createSArguments(domain, conditionEnforced,
            new Object[] {loopCondition}));

    try {
      // TODO: this is a simplification, we don't cover the case receiver isn't a boolean
      while (loopConditionResult == predicateBool) {
        bodyValueSend.call(frame, SArguments.createSArguments(domain,
            bodyEnforced, new Object[] {loopBody}));
        loopConditionResult = (boolean) conditionValueSend.call(
            frame, SArguments.createSArguments(domain, conditionEnforced,
                new Object[] {loopCondition}));

        if (CompilerDirectives.inInterpreter()) {
          iterationCount++;
        }
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        reportLoopCount(iterationCount);
      }
    }
    return universe.nilObject;
  }

  protected abstract SObject doWhileConditionally(final VirtualFrame frame,
      final SBlock loopCondition, final SBlock loopBody);

  protected final void reportLoopCount(final long count) {
    CompilerAsserts.neverPartOfCompilation();
    Node current = getParent();
    while (current != null && !(current instanceof RootNode)) {
      current = current.getParent();
    }
    if (current != null) {
      ((AbstractInvokable) current).propagateLoopCountThroughoutLexicalScope(count);
    }
  }
}
