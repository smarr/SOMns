package som.interpreter.actors;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

import som.VM;
import som.interpreter.actors.EventualMessage.PromiseCallbackMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.EventualMessage.PromiseSendMessage;


/**
 * WARNING: This node needs to be used in a context that makes sure
 * that access to the promise is properly synchronized!
 */
public abstract class SchedulePromiseHandlerNode extends Node {

  protected static final WrapReferenceNode createWrapper() {
    return WrapReferenceNodeGen.create();
  }

  public abstract void execute(SPromise promise, PromiseMessage msg, Actor current);

  @Specialization
  public final void schedule(final SPromise promise,
      final PromiseCallbackMessage msg, final Actor current,
      @Cached("createWrapper()") final WrapReferenceNode wrapper) {
    assert promise.getOwner() != null;

    msg.args[PromiseMessage.PROMISE_VALUE_IDX] = wrapper.execute(
        promise.getValueUnsync(), msg.originalSender, current);
    msg.originalSender.send(msg);
  }

  @Specialization
  public final void schedule(final SPromise promise,
      final PromiseSendMessage msg, final Actor current,
      @Cached("createWrapper()") final WrapReferenceNode rcvrWrapper) {
    VM.thisMethodNeedsToBeOptimized("Still needs to get out the extra cases and the wrapping");
    assert promise.getOwner() != null;

    Actor finalTarget = promise.getOwner();

    Object receiver = rcvrWrapper.execute(promise.getValueUnsync(),
        finalTarget, current);
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
      msg.args[i] = finalTarget.wrapForUse(msg.args[i], msg.originalSender, null);
    }

    msg.target      = finalTarget; // for sends to far references, we need to adjust the target
    msg.finalSender = current;

    finalTarget.send(msg);
  }
}
