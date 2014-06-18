package som.interpreter.nodes.enforced;

import som.interpreter.SArguments;
import som.interpreter.Types;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.ISuperReadNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;


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

  @ExplodeLoop
  public static Object[] getArgumentsWithoutReceiver(final Object[] arguments) {
    Object[] argsArr = new Object[arguments.length - 1];
    for (int i = 1; i < arguments.length; i++) {
      argsArr[i - 1] = arguments[i];
    }
    return argsArr;
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

    SObject domain;
    SInvokable handler;
    SClass rcvrClass = Types.getClassOf(rcvr, Universe.current());

    if (isSuperSend()) {
      rcvrClass = (SClass) rcvrClass.getSuperClass();
    }

    if (rcvr instanceof SAbstractObject) {
      domain = ((SAbstractObject) rcvr).getDomain();

    } else if (rcvr instanceof Object[]) {
      domain = SArray.getOwner((Object[]) rcvr);
    } else {
      domain = Universe.current().standardDomain;
    }

    handler = domain.getSOMClass(Universe.current()).lookupInvokable(intercessionHandler);
    return handler.invoke(domain, false, domain, selector,
        SArray.fromArgArrayWithReceiverToSArrayWithoutReceiver(
            args, SArguments.domain(frame)),
            rcvr, rcvrClass);
  }

}
