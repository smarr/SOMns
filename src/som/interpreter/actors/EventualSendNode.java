package som.interpreter.actors;

import som.VM;
import som.compiler.ClassBuilder.ClassDefinitionId;
import som.compiler.MethodBuilder;
import som.interpreter.Method;
import som.interpreter.nodes.ArgumentReadNode.LocalArgumentReadNode;
import som.interpreter.nodes.ArgumentReadNode.LocalSelfReadNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vm.Symbols;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;


public final class EventualSendNode extends AbstractMessageSendNode {

  protected final SSymbol selector;

  protected final RootCallTarget onReceive;

  private static final ClassDefinitionId fakeId = new ClassDefinitionId(Symbols.symbolFor("--fake--"));

  public EventualSendNode(final SSymbol selector,
      final ExpressionNode[] arguments, final SourceSection source) {
    super(arguments, source);
    this.selector = selector;

    MethodBuilder eventualInvoke = new MethodBuilder(true);
    ExpressionNode[] args = new ExpressionNode[arguments.length];
    args[0] = new LocalSelfReadNode(fakeId, null);
    for (int i = 1; i < arguments.length; i++) {
      args[i] = new LocalArgumentReadNode(i, null);
    }
    AbstractMessageSendNode invoke = MessageSendNode.createMessageSend(selector, args, source);

    Method m = new Method(source, invoke,
        eventualInvoke.getCurrentMethodScope(),
        (ExpressionNode) invoke.deepCopy());

    onReceive = m.createCallTarget();
  }

  @Override
  public SPromise doPreEvaluated(final VirtualFrame frame, final Object[] args) {
    if (CompilerDirectives.inInterpreter()) {
      // TODO: this can be done unconditionally in some uninitialized version
      //       of EventualSendNode, which we probably should have at some point
      VM.hasSendMessages();
    }

    // TODO: should we specialize here? or in the processing actor?
    //       technically, I think it is safe to do and cache the lookup here
    //       but, it depends on how we do the actual send,
    //       do we create a new root node that is queued, or is there something else?
    //       for the moment, the event loop will probably just use an indirect
    //       call node after the lookup.

    Object rcvrObject = args[0];
    if (rcvrObject instanceof SFarReference) {
      SFarReference rcvr = (SFarReference) args[0];
      return rcvr.eventualSend(
          EventualMessage.getActorCurrentMessageIsExecutionOn(), selector, args);
    } else if (rcvrObject instanceof SPromise) {
      SPromise rcvr = (SPromise) rcvrObject;
      return rcvr.whenResolved(selector, args, onReceive);
    } else {
      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
      return current.eventualSend(current, selector, args);
    }
  }

  @Override
  public String toString() {
    return "EventSend[" + selector.toString() + "]";
  }
}
