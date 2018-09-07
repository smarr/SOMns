package som.interpreter.actors;

import java.util.concurrent.ForkJoinPool;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.IntValueProfile;

import som.VM;
import som.interpreter.SArguments;
import som.interpreter.actors.EventualMessage.PromiseCallbackMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.EventualMessage.PromiseSendMessage;
import som.interpreter.nodes.ExpressionNode;
import tools.asyncstacktraces.AsyncShadowStackEntry;


/**
 * WARNING: This node needs to be used in a context that makes sure
 * that access to the promise is properly synchronized!
 */
public abstract class SchedulePromiseHandlerNode extends Node {

  protected static final WrapReferenceNode createWrapper() {
    return WrapReferenceNodeGen.create();
  }

  private final ForkJoinPool actorPool;

  protected SchedulePromiseHandlerNode(final ForkJoinPool actorPool) {
    this.actorPool = actorPool;
  }

  public abstract void execute(SPromise promise, PromiseMessage msg, Actor current);

  @Specialization
  public final void schedule(final SPromise promise,
      final PromiseCallbackMessage msg, final Actor current,
      @Cached("createWrapper()") final WrapReferenceNode wrapper) {
    assert promise.getOwner() != null;

    msg.args[PromiseMessage.PROMISE_VALUE_IDX] = wrapper.execute(
        promise.getValueUnsync(), msg.originalSender, current);

    // TODO: I think, we need the info about the resolution context from the promise
    // we want to know where it was resolved, where the value is coming from
    AsyncShadowStackEntry newEntry =
        new AsyncShadowStackEntry(null, (ExpressionNode) this.getParent().getParent());

    SArguments.setShadowStackEntry(msg.args, newEntry);
    msg.originalSender.send(msg, actorPool);
  }

  @Specialization
  public final void schedule(final SPromise promise,
      final PromiseSendMessage msg, final Actor current,
      @Cached("createWrapper()") final WrapReferenceNode rcvrWrapper,
      @Cached("createWrapper()") final WrapReferenceNode argWrapper) {
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
      finalTarget = ((SFarReference) receiver).getActor();
      receiver = ((SFarReference) receiver).getValue();
    }

    msg.args[PromiseMessage.PROMISE_RCVR_IDX] = receiver;

    assert !(receiver instanceof SFarReference) : "this should not happen, because we need to redirect messages to the other actor, and normally we just unwrapped this";
    assert !(receiver instanceof SPromise);

    wrapArguments(msg, finalTarget, argWrapper);

    msg.target = finalTarget; // for sends to far references, we need to adjust the target
    msg.finalSender = current;

    finalTarget.send(msg, actorPool);
  }

  private final IntValueProfile numArgs = IntValueProfile.createIdentityProfile();

  @ExplodeLoop
  private void wrapArguments(final PromiseSendMessage msg, final Actor finalTarget,
      final WrapReferenceNode argWrapper) {
    // TODO: break that out into nodes
    for (int i =
        1; i < numArgs.profile(SArguments.getLengthWithoutShadowStack(msg.args)); i++) {
      msg.args[i] = argWrapper.execute(msg.args[i], finalTarget, msg.originalSender);
    }

  }
}
