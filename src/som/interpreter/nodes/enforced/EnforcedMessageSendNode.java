package som.interpreter.nodes.enforced;

import som.interpreter.SArguments;
import som.interpreter.Types;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.ISuperReadNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractUninitializedMessageSendNode;
import som.interpreter.nodes.PreevaluatedExpression;
import som.interpreter.nodes.dispatch.DispatchChain.Cost;
import som.interpreter.nodes.enforced.IntercessionHandlerCache.AbstractIntercessionHandlerDispatch;
import som.vm.Universe;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SDomain;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;


public class EnforcedMessageSendNode extends AbstractMessageSendNode {

  protected final SSymbol selector;
  protected final Universe universe;
  @Child protected AbstractIntercessionHandlerDispatch dispatch;

  public EnforcedMessageSendNode(final SSymbol selector,
      final ExpressionNode[] arguments,
      final SourceSection source) {
    super(arguments, source, true);
    this.selector = selector;
    dispatch = IntercessionHandlerCache.create("requestExecutionOf:with:on:lookup:", executesEnforced);
    universe = Universe.current();
  }

  @Override
  public Object doPreEvaluated(final VirtualFrame frame, final Object[] args) {
    Object  rcvr = args[0];
    SObject rcvrDomain    = SDomain.getOwner(rcvr);
    SObject currentDomain = SArguments.domain(frame);
    SClass  rcvrClass = Types.getClassOf(rcvr, universe);

    Object[] arguments = SArguments.createSArgumentsArray(false, currentDomain,
        rcvrDomain, selector,
        SArray.fromArgArrayWithReceiverToSArrayWithoutReceiver(args, currentDomain),
        rcvr, rcvrClass);

    return dispatch.executeDispatch(frame, rcvrDomain, arguments);
  }

  private static final class EnforcedSuperMessageSendNode extends EnforcedMessageSendNode {
    private final SClass superClass;

    public EnforcedSuperMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments, final SourceSection source) {
      super(selector, arguments, source);
      superClass = ((ISuperReadNode) arguments[0]).getSuperClass();
    }

    @Override
    public Object doPreEvaluated(final VirtualFrame frame, final Object[] args) {
      Object  rcvr = args[0];
      SObject rcvrDomain    = SDomain.getOwner(rcvr);
      SObject currentDomain = SArguments.domain(frame);

      Object[] arguments = SArguments.createSArgumentsArray(false, currentDomain,
          rcvrDomain, selector,
          SArray.fromArgArrayWithReceiverToSArrayWithoutReceiver(args, currentDomain),
          rcvr, superClass);

      return dispatch.executeDispatch(frame, rcvrDomain, arguments);
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
