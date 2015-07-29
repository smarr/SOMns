package som.interpreter.actors;

import java.util.ArrayDeque;
import java.util.NoSuchElementException;
import java.util.concurrent.ForkJoinPool;

import som.interpreter.actors.SPromise.SResolver;
import som.primitives.ObjectPrims.IsValue;
import som.vmobjects.SSymbol;


// design goals:
//  - avoid 1-thread per actor
//  - have a low-overhead and safe scheduling system
//  - use an executor or fork/join pool for execution
//  - each actor should only have at max. one active task


//  algorithmic sketch
//   - enqueue message in actor queue
//   - check whether we need to submit it to the pool
//   - could perhaps be a simple boolean flag?
//   - at the end of a turn, we take the next message, and
//   - submit a new task to the pool

// TODO: figure out whether there is a simple look free design commonly used
public class Actor {
  private final ArrayDeque<EventualMessage> mailbox;
  private boolean isExecuting;

  public Actor() {
    mailbox     = new ArrayDeque<>();
    isExecuting = false;
  }

  /**
   * This constructor should only be used for the main actor!
   */
  public Actor(final boolean isMainActor) {
    this();
    assert isMainActor;
    isExecuting = true;
  }

  public SPromise eventualSend(final Actor currentActor, final SSymbol selector,
      final Object[] args) {
    SPromise result   = new SPromise(currentActor);
    SResolver resolver = new SResolver(result);

    EventualMessage msg;
    if (currentActor == this) {
      msg = new EventualMessage(this, selector, args, resolver);
    } else {
      // TODO: i think we can ignore the receiver, that should already be ok
      for (int i = 1; i < args.length; i++) {
        Object o = args[i];
        if (!IsValue.isObjectValue(o)) {
          args[i] = new SFarReference(currentActor, o);
        }
      }
      msg = new EventualMessage(this, selector, args, resolver);
    }
    enqueueMessage(msg);

    return result;
  }

  public synchronized void enqueueMessage(final EventualMessage msg) {
    if (isExecuting) {
      mailbox.add(msg);
    } else {
      ForkJoinPool.commonPool().submit(msg);
      isExecuting = true;
    }
  }

  /**
   * This method is only to be called from the EventualMessage task, and the
   * main Actor in Bootstrap.executeApplication().
   */
  public synchronized void enqueueNextMessageForProcessing() {
    try {
      EventualMessage nextTask = mailbox.remove();
      assert isExecuting;
      ForkJoinPool.commonPool().submit(nextTask);
      return;
    } catch (NoSuchElementException e) {
      isExecuting = false;
    }
  }
}
