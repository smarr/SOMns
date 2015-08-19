package som.interpreter.actors;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.concurrent.ForkJoinPool;

import som.VM;
import som.interpreter.actors.EventualMessage.DirectMessage;
import som.interpreter.actors.SPromise.SResolver;
import som.primitives.ObjectPrims.IsValue;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;


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

  /**
   * @return main actor
   */
  public static Actor initializeActorSystem() {
    Actor mainActor = createActor(true);
    EventualMessage.setMainActor(mainActor);
    return mainActor;
  }

  private static Actor createActor(final boolean isMainActor) {
    if (VM.DebugMode) {
      return new DebugActor(isMainActor);
    } else {
      return new Actor(isMainActor);
    }
  }

  public static Actor createActor() {
    if (VM.DebugMode) {
      return new DebugActor();
    } else {
      return new Actor();
    }
  }

  private final ArrayDeque<EventualMessage> mailbox = new ArrayDeque<>();
  private boolean isExecuting;

  protected Actor() {
    isExecuting = false;
  }

  /**
   * This constructor should only be used for the main actor!
   */
  protected Actor(final boolean isMainActor) {
    assert isMainActor;
    isExecuting = true;
  }

  public final SPromise eventualSend(final Actor currentActor, final SSymbol selector,
      final Object[] args) {
    SPromise  result   = SPromise.createPromise(currentActor);
    SResolver resolver = SPromise.createResolver(result, "eventualSend:", selector);

    CompilerAsserts.neverPartOfCompilation("This needs to be optimized");

    DirectMessage msg = new DirectMessage(this, selector, args, currentActor, resolver);
    msg.getTarget().enqueueMessage(msg);

    return result;
  }

  public final Object wrapForUse(final Object o, final Actor owner) {
    CompilerAsserts.neverPartOfCompilation("This should probably be optimized");
    if (this == owner) {
      return o;
    }

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

      SPromise remote = SPromise.createPromise(this);
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

  protected void logMessageAddedToMailbox(final EventualMessage msg) { }
  protected void logMessageBeingExecuted(final EventualMessage msg) { }
  protected void logNoTaskForActor() { }

  public final synchronized void enqueueMessage(final EventualMessage msg) {
    assert msg.getTarget() == this;
    if (isExecuting) {
      mailbox.add(msg);
      logMessageAddedToMailbox(msg);
    } else {
      executeOnPool(msg);
      logMessageBeingExecuted(msg);
      isExecuting = true;
    }
  }

  @TruffleBoundary
  public static void executeOnPool(final EventualMessage msg) {
    actorPool.execute(msg);
  }

  public static boolean isPoolIdle() {
    return !actorPool.hasQueuedSubmissions() && actorPool.getActiveThreadCount() == 0;
  }

  private static final ForkJoinPool actorPool = new ForkJoinPool();

  /**
   * This method is only to be called from the EventualMessage task, and the
   * main Actor in Bootstrap.executeApplication().
   */
  public final synchronized void enqueueNextMessageForProcessing() {
    try {
      EventualMessage nextTask = mailbox.remove();
      assert isExecuting;
      executeOnPool(nextTask);
      logMessageBeingExecuted(nextTask);
      return;
    } catch (NoSuchElementException e) {
      logNoTaskForActor();
      isExecuting = false;
    }
  }

  @Override
  public String toString() {
    return "Actor";
  }

  public static final class DebugActor extends Actor {
    private static final ArrayList<Actor> actors = new ArrayList<Actor>();

    private final boolean isMain;
    private final int id;

    public DebugActor() {
      super();
      isMain = false;
      synchronized (actors) {
        actors.add(this);
        id = actors.size() - 1;
      }
    }

    public DebugActor(final boolean isMainActor) {
      super(isMainActor);
      this.isMain = isMainActor;
      synchronized (actors) {
        actors.add(this);
        id = actors.size() - 1;
      }
    }

    @Override
    protected void logMessageAddedToMailbox(final EventualMessage msg) {
      VM.errorPrintln(toString() + ": queued task " + msg.toString());
    }

    @Override
    protected void logMessageBeingExecuted(final EventualMessage msg) {
      VM.errorPrintln(toString() + ": execute task " + msg.toString());
    }

    @Override
    protected void logNoTaskForActor() {
      VM.errorPrintln(toString() + ": no task");
    }

    @Override
    public String toString() {
      return "Actor[" + (isMain ? "main" : id) + "]";
    }
  }
}
