package som.interpreter.actors;

import som.VM;
import som.interpreter.actors.EventualMessage.DirectMessage;
import som.interpreter.actors.EventualMessage.PromiseSendMessage;
import som.interpreter.actors.RegisterOnPromiseNode.RegisterWhenResolved;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.InternalObjectArrayNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;


@NodeChild(value = "arguments", type = InternalObjectArrayNode.class)
public abstract class EventualSendNode extends ExpressionNode {

  protected final SSymbol selector;

  public EventualSendNode(final SSymbol selector,
      final int numArgs, final SourceSection source) {
    super(source);
    this.selector = selector;
  }

  @Child protected RegisterWhenResolved registerNode = new RegisterWhenResolved();

  @Specialization
  public final SPromise send(final Object[] args) {
    // no arguments, should be compiled to assertion and only executed once
    if (CompilerDirectives.inInterpreter()) {
      VM.hasSendMessages();
    }

    if (args[0] instanceof SFarReference) {
      Actor owner = EventualMessage.getActorCurrentMessageIsExecutionOn();

      SPromise  result   = SPromise.createPromise(owner);
      SResolver resolver = SPromise.createResolver(result, "eventualSend:", selector);

      sendDirectMessage(args, owner, resolver);

      return result;
    } else if (args[0] instanceof SPromise) {
      SPromise rcvr = (SPromise) args[0];
      SPromise  promise  = SPromise.createPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
      SResolver resolver = SPromise.createResolver(promise, "eventualSendToPromise:", selector);

      sendPromiseMessage(args, rcvr, resolver, registerNode);
      return promise;
    } else {
      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
      SPromise  result   = SPromise.createPromise(current);
      SResolver resolver = SPromise.createResolver(result, "eventualSend:", selector);

      DirectMessage msg = new DirectMessage(current, selector, args, current,
          resolver);
      current.enqueueMessage(msg);

      return result;
    }
  }

  @ExplodeLoop
  private void sendDirectMessage(final Object[] args, final Actor owner,
      final SResolver resolver) {
    CompilerAsserts.compilationConstant(args.length);

    SFarReference rcvr = (SFarReference) args[0];
    Actor target = rcvr.getActor();

    for (int i = 0; i < args.length; i++) {
      args[i] = target.wrapForUse(args[i], owner);
    }

    assert !(args[0] instanceof SFarReference) : "This should not happen for this specialization, but it is handled in determineTargetAndWrapArguments(.)";
    assert !(args[0] instanceof SPromise) : "Should not happen either, but just to be sure";


    DirectMessage msg = new DirectMessage(target, selector, args, owner,
        resolver);
    target.enqueueMessage(msg);
  }


  private void sendPromiseMessage(final Object[] args, final SPromise rcvr,
      final SResolver resolver, final RegisterWhenResolved register) {
    assert rcvr.getOwner() == EventualMessage.getActorCurrentMessageIsExecutionOn() : "think this should be true because the promise is an Object and owned by this specific actor";
    PromiseSendMessage msg = new PromiseSendMessage(selector, args, rcvr.getOwner(), resolver);

    register.register(rcvr, msg, rcvr.getOwner());
  }

  @Override
  public String toString() {
    return "EventSend[" + selector.toString() + "]";
  }
}
