package som.interpreter.actors;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.concurrent.ForkJoinPool;

import som.interpreter.actors.SPromise.SResolver;
import som.primitives.ObjectPrims.IsValue;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;


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
  private final ArrayDeque<EventualMessage> mailbox = new ArrayDeque<>();
  private boolean isExecuting;
  private final boolean isMain;
  private final int id;

  private static final ArrayList<Actor> actors = new ArrayList<Actor>();

  public Actor() {
    isExecuting = false;
    isMain      = false;
    synchronized (actors) {
      actors.add(this);
      id = actors.size() - 1;
    }
  }

  /**
   * This constructor should only be used for the main actor!
   */
  public Actor(final boolean isMainActor) {
    assert isMainActor;
    isExecuting = true;
    isMain      = true;
    synchronized (actors) {
      actors.add(this);
      id = actors.size() - 1;
    }
  }

  public SPromise eventualSend(final Actor currentActor, final SSymbol selector,
      final Object[] args) {
    SPromise result   = new SPromise(currentActor);
    SResolver resolver = new SResolver(result);

    CompilerAsserts.neverPartOfCompilation("This needs to be optimized");

    EventualMessage msg;
    if (currentActor == this) {
      // self send, no arg handling needed, they come straight from the same actor
      msg = new EventualMessage(this, selector, args, resolver, currentActor);
    } else {
      for (int i = 0; i < args.length; i++) {
        args[i] = wrapForUse(args[i], currentActor);

      }
      msg = new EventualMessage(this, selector, args, resolver, currentActor);
    }
    enqueueMessage(msg);

    return result;
  }

  public Object wrapForUse(final Object o, final Actor owner) {
    CompilerAsserts.neverPartOfCompilation("This should probably be optimized");
    if (o instanceof SFarReference) {
      if (((SFarReference) o).getActor() == this) {
        return ((SFarReference) o).getValue();
      }
    } else if (o instanceof SPromise) {
      // promises cannot just be wrapped in far references, instead, other actors
      // should get a new promise that is going to be resolved once the original
      // promise gets resolved

      SPromise orgProm = (SPromise) o;
      // assert orgProm.getOwner() == owner; this can be another actor, which initialized a scheduled eventual send by resolving a promise, that's the promise pipelining...
      if (orgProm.getOwner() == this) {
        return orgProm;
      }

      SPromise remote = new SPromise(this);
      synchronized (orgProm) {
        if (orgProm.isSomehowResolved()) {
          orgProm.copyValueToRemotePromise(remote);
        } else {
          orgProm.addChainedPromise(remote);
        }
        return remote;
      }
    } else if (!IsValue.isObjectValue(o)) {
      if (this != owner) {
        return new SFarReference(owner, o);
      }
    }
    return o;
  }

  public synchronized void enqueueMessage(final EventualMessage msg) {
    assert msg.isReceiverSet();

    if (isExecuting) {
      mailbox.add(msg);
    } else {
      ForkJoinPool.commonPool().execute(msg);
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
      ForkJoinPool.commonPool().execute(nextTask);
      return;
    } catch (NoSuchElementException e) {
      isExecuting = false;
    }
  }

  @Override
  public String toString() {
    return "Actor[" + (isMain ? "main" : id) + "]";
  }
}
