package som.interpreter.actors;

import som.interpreter.actors.EventualMessage.PromiseCallbackMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.EventualMessage.PromiseSendMessage;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;


/**
 * WARNING: This node needs to be used in a context that makes sure
 * that access to the promise is properly synchronized!
 */
public abstract class SchedulePromiseHandlerNode extends Node {

  public abstract void execute(final SPromise promise, final PromiseMessage msg, final Actor current);

  @Specialization
  public final void schedule(final SPromise promise,
      final PromiseMessage message, final Actor current) {
    assert promise.getOwner() != null;

    if (message instanceof PromiseCallbackMessage) {
      PromiseCallbackMessage msg = (PromiseCallbackMessage) message;
      msg.args[PromiseMessage.PROMISE_VALUE_IDX] =
          msg.originalSender.wrapForUse(promise.getValueUnsync(), current);
      msg.originalSender.enqueueMessage(msg);
      return;
    }

    PromiseSendMessage msg = (PromiseSendMessage) message;

    Actor finalTarget = promise.getOwner();

    Object receiver = finalTarget.wrapForUse(promise.getValueUnsync(), current);
    assert !(receiver instanceof SPromise) : "TODO: handle this case as well?? Is it possible? didn't think about it";

    // TODO: might want to handle that in a specialization
    if (receiver instanceof SFarReference) {
      // now we are about to send a message to a far reference, so, it
      // is better to just redirect the message back to the current actor
      finalTarget   = ((SFarReference) receiver).getActor();
      receiver = ((SFarReference) receiver).getValue();
    }

    msg.args[PromiseMessage.PROMISE_RCVR_IDX] = receiver;

    assert !(receiver instanceof SFarReference) : "this should not happen, because we need to redirect messages to the other actor, and normally we just unwrapped this";
    assert !(receiver instanceof SPromise);

    // TODO: break that out into nodes
    for (int i = 1; i < msg.args.length; i++) {
      msg.args[i] = finalTarget.wrapForUse(msg.args[i], msg.originalSender);
    }

    msg.target      = finalTarget; // for sends to far references, we need to adjust the target
    msg.finalSender = current;

    finalTarget.enqueueMessage(msg);
  }
}
