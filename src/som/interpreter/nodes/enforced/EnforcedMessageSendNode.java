package som.interpreter.nodes.enforced;

import som.interpreter.SArguments;
import som.interpreter.Types;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.ISuperReadNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractUninitializedMessageSendNode;
import som.interpreter.nodes.PreevaluatedExpression;
import som.vm.Universe;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SDomain;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.VirtualFrame;


public class EnforcedMessageSendNode extends AbstractMessageSendNode {

  private final SSymbol selector;
  private final SSymbol intercessionHandler;

  public EnforcedMessageSendNode(final SSymbol selector,
      final ExpressionNode[] arguments,
      final SourceSection source) {
    super(arguments, source, true);
    this.selector = selector;
    intercessionHandler = Universe.current().symbolFor("requestExecutionOf:with:on:lookup:");
  }

  private boolean isSuperSend() {
    // first option is a super send, super sends are treated specially because
    // the receiver class is lexically determined
    return argumentNodes[0] instanceof ISuperReadNode;
  }

  @Override
  public Object doPreEvaluated(final VirtualFrame frame, final Object[] args) {
    Object rcvr = args[0];

    // TODO need proper support for everything else...
    // arrays are most problematic, but can be solved by going to 1-based direct
    // indexing and using the slot 0 for the owner domain. Only support Object[] anyway.

    SObject rcvrDomain;
    SInvokable handler;
    SClass rcvrClass = Types.getClassOf(rcvr, Universe.current());

    if (isSuperSend()) {
      rcvrClass = (SClass) rcvrClass.getSuperClass();
    }

    rcvrDomain = SDomain.getOwner(rcvr);

    handler = rcvrDomain.getSOMClass(Universe.current()).lookupInvokable(intercessionHandler);
    SObject currentDomain = SArguments.domain(frame);
    return handler.invoke(currentDomain, false, rcvrDomain, selector,
        SArray.fromArgArrayWithReceiverToSArrayWithoutReceiver(
            args, currentDomain),
            rcvr, rcvrClass);
  }

  public static final class UninitializedEnforcedMessageSendNode
    extends AbstractUninitializedMessageSendNode {

    public UninitializedEnforcedMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments, final SourceSection source) {
      super(selector, arguments, source, true);
    }

    @Override
    protected PreevaluatedExpression makeSuperSend() {
      // since EnforcedMessageSendNode handles super sends explicitly, generic
      // is good enough
      return makeGenericSend();
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
}
