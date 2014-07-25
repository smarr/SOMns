package som.interpreter.nodes.enforced;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.ISuperReadNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractUninitializedMessageSendNode;
import som.interpreter.nodes.PreevaluatedExpression;
import som.interpreter.nodes.dispatch.DispatchChain.Cost;
import som.interpreter.nodes.enforced.DomainAndClassDispatch.AbstractDomainAndClassDispatch;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;


public class EnforcedMessageSendNode extends AbstractMessageSendNode {

  protected final SSymbol selector;
  @Child protected AbstractDomainAndClassDispatch dispatch;

  public EnforcedMessageSendNode(final SSymbol selector,
      final ExpressionNode[] arguments,
      final SourceSection source) {
    super(arguments, source, true);
    this.selector = selector;
    dispatch = DomainAndClassDispatch.create("requestExecutionOf:with:on:lookup:",
        executesEnforced, selector);
  }

  @Override
  public Object doPreEvaluated(final VirtualFrame frame, final Object[] args) {
    return dispatch.executeDispatch(frame, args);
  }

  private static final class EnforcedSuperMessageSendNode extends EnforcedMessageSendNode {
    private final SClass superClass;

    public EnforcedSuperMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments, final SourceSection source) {
      super(selector, arguments, source);
      superClass = ((ISuperReadNode) arguments[0]).getSuperClass();
      dispatch = DomainAndClassDispatch.create("requestExecutionOf:with:on:lookup:",
          executesEnforced, selector, superClass);
    }

    @Override
    public Object doPreEvaluated(final VirtualFrame frame, final Object[] args) {
      return dispatch.executeDispatch(frame, args);
    }
  }

  public static final class UninitializedEnforcedMessageSendNode
    extends AbstractUninitializedMessageSendNode {

    public UninitializedEnforcedMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments, final SourceSection source) {
      super(selector, arguments, source, true);
    }

    @Override
    protected PreevaluatedExpression makeSuperSend() {
      EnforcedSuperMessageSendNode send = new EnforcedSuperMessageSendNode(selector,
          argumentNodes, getSourceSection());
      return replace(send);
    }

    @Override
    protected PreevaluatedExpression specializeUnary(final Object[] arguments) { return this; }

    @Override
    protected PreevaluatedExpression specializeBinary(final Object[] arguments) { return this; }

    @Override
    protected PreevaluatedExpression specializeTernary(final Object[] arguments) { return this; }

    @Override
    protected PreevaluatedExpression specializeQuaternary(final Object[] arguments) { return this; }

    @Override
    protected AbstractMessageSendNode makeGenericSend() {
      EnforcedMessageSendNode send = new EnforcedMessageSendNode(selector,
          argumentNodes, getSourceSection());
      return replace(send);
    }
  }

  @Override
  public final NodeCost getCost() {
    return Cost.getCost(dispatch);
  }
}
