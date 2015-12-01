package som.interpreter.actors;

import som.interpreter.actors.EventualMessage.PromiseMessage;

import com.oracle.truffle.api.nodes.Node;


public abstract class RegisterOnPromiseNode {

  public static final class RegisterWhenResolved extends Node {
    @Child protected SchedulePromiseHandlerNode schedule = SchedulePromiseHandlerNodeGen.create();

    public void register(final SPromise promise, final PromiseMessage msg,
        final Actor current) {

      Object promiseValue;
      // LOCKING NOTE: this handling of the check for resolution and the
      // registering of the callback/msg needs to be done in the same step
      // otherwise, we are opening a race between completion and processing
      // of handlers. We can split the case for a resolved promise out, because
      // we need to schedule the callback/msg directly anyway
      synchronized (promise) {
        if (!promise.isResolvedUnsync()) {
          if (promise.isErroredUnsync()) {
            // short cut on error, this promise will never error, so,
            // just return promise, don't use isSomehowResolved(), because the other
            // case are not correct
            return;
          }
          promise.registerWhenResolvedUnsynced(msg);
          return;
        } else {
          promiseValue = promise.value;
          assert promiseValue != null;
        }
      }

      // LOCKING NOTE:
      // The schedule.execute() is moved out of the synchronized(promise) block,
      // because it leads to wrapping of arguments, which needs to look promises
      // and thus, could lead to a deadlock.
      //
      // However, we still need to wait until any other thread is done with
      // resolving the promise, thus we lock on the promise's value, which is
      // used to group setting the promise resolution state and processing the
      // message.
      synchronized (promiseValue) {
        schedule.execute(promise, msg, current);
      }
    }
  }

  public static final class RegisterOnError extends Node {

  }
}
