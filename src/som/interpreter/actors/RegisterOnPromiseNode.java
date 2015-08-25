package som.interpreter.actors;

import som.interpreter.actors.EventualMessage.PromiseMessage;

import com.oracle.truffle.api.nodes.Node;


public abstract class RegisterOnPromiseNode {

  public static final class RegisterWhenResolved extends Node {
    public void register(final SPromise promise, final PromiseMessage msg,
        final Actor current) {
      synchronized (promise) {
        if (promise.isResolvedUnsync()) {
          promise.scheduleCallbacksOnResolution(promise.getValueUnsync(), msg, current);
        } else {
          if (promise.isErroredUnsync()) {
            // short cut on error, this promise will never error, so,
            // just return promise, don't use isSomehowResolved(), because the other
            // case are not correct
            return;
          }
          promise.registerWhenResolvedUnsynced(msg);
        }
      }
    }
  }

  public static final class RegisterOnError extends Node {

  }
}
