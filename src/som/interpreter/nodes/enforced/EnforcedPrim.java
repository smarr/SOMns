package som.interpreter.nodes.enforced;

import som.interpreter.SArguments;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.DispatchChain.Cost;
import som.interpreter.nodes.enforced.IntercessionHandlerCache.AbstractIntercessionHandlerDispatch;
import som.vmobjects.SArray;
import som.vmobjects.SDomain.GetOwnerNode;
import som.vmobjects.SInvokable.SPrimitive;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeCost;


public final class EnforcedPrim extends ExpressionNode {
  public static final String INTERCESSION_SIGNATURE = "requestExecutionOfPrimitive:with:on:";

  public static EnforcedPrim create(final ExpressionNode receiver,
      final ExpressionNode[] arguments) {
    return new EnforcedPrim(receiver, arguments);
  }

  public static EnforcedPrim create(final ExpressionNode[] arguments) {
    assert arguments.length >= 1;
    ExpressionNode   rcvr = arguments[0];
    ExpressionNode[] args = new ExpressionNode[arguments.length - 1];

    for (int i = 1; i < arguments.length; i++) {
      args[i - 1] = arguments[i];
    }

    return new EnforcedPrim(rcvr, args);
  }

  @Child    private       ExpressionNode   receiver;
  @Children private final ExpressionNode[] arguments;
  @Child    private       AbstractIntercessionHandlerDispatch dispatch;
  private final GetOwnerNode getOwner;

  @CompilationFinal private SPrimitive primitive;

  private EnforcedPrim(final ExpressionNode receiver,
      final ExpressionNode[] arguments) {
    super(null, true);
    this.receiver  = receiver;
    this.arguments = arguments;
    dispatch = IntercessionHandlerCache.create(INTERCESSION_SIGNATURE, executesEnforced);
    getOwner = new GetOwnerNode();
  }

  public void setPrimitive(final SPrimitive primitive) {
    this.primitive = primitive;
  }

  @ExplodeLoop
  private Object[] determineArgumentArray(final VirtualFrame frame) {
    SObject domain = SArguments.domain(frame);

    Object[] result = new Object[arguments.length + 1];
    result[0] = domain;

    for (int i = 0; i < arguments.length; i++) {
      result[i + 1] = arguments[i].executeGeneric(frame);
    }
    return result;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    Object rcvr = receiver.executeGeneric(frame);
    Object[] args = determineArgumentArray(frame);
    return executeEvaluated(frame, rcvr, args);
  }

  @Override
  public void executeVoid(final VirtualFrame frame) {
    executeGeneric(frame);
  }

  public Object executeEvaluated(final VirtualFrame frame,
      final Object receiver, final Object[] args) {
    SObject currentDomain = SArguments.domain(frame);
    SObject rcvrDomain    = getOwner.getOwner(receiver);

    Object[] arguments = SArguments.createSArgumentsArray(false, currentDomain,
        rcvrDomain, primitive,
        SArray.fromArgArrayWithReceiverToSArrayWithoutReceiver(args, currentDomain), receiver);
    return dispatch.executeDispatch(frame, rcvrDomain, arguments);
  }

  @Override
  public NodeCost getCost() {
    return Cost.getCost(dispatch);
  }
}
