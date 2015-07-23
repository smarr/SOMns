package som.interpreter.actors;

import som.VM;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;


public final class EventualSendNode extends AbstractMessageSendNode {

  protected final SSymbol selector;

  public EventualSendNode(final SSymbol selector,
      final ExpressionNode[] arguments, final SourceSection source) {
    super(arguments, source);
    this.selector = selector;
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
    SFarReference rcvr = (SFarReference) args[0];
    return rcvr.eventualSend(
        EventualMessage.getActorCurrentMessageIsExecutionOn(), selector, args);
  }
}
