package som.interpreter.actors;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;

import som.VM;
import som.VmSettings;
import som.primitives.ObjectPrims.IsValue;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray.STransferArray;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;


/**
 * Represent's a language level actor
 *
 * design goals:
 * - avoid 1-thread per actor
 * - have a low-overhead and safe scheduling system
 * - use an executor or fork/join pool for execution
 * - each actor should only have at max. one active task
 *
 * algorithmic sketch
 *  - enqueue message in actor queue
 *  - execution is done by a special ExecAllMessages task
 *    - this task is submitted to the f/j pool
 *    - once it is executing, it goes to the actor,
 *    - grabs the current mailbox
 *    - and sequentially executes all messages
 */
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

  /** Buffer for incoming messages. */
  private ArrayList<EventualMessage> mailbox = new ArrayList<>();

  /** Flag to indicate whether there is currently a F/J task executing. */
  private boolean isExecuting;

  /** Is scheduled on the pool, and executes messages to this actor. */
  private final ExecAllMessages executor;

  protected Actor() {
    isExecuting = false;
    executor = new ExecAllMessages(this);
  }

  /**
   * This constructor should only be used for the main actor!
   */
  protected Actor(final boolean isMainActor) {
    assert isMainActor;
    isExecuting = true;
    executor = new ExecAllMessages(this);
  }

  public final Object wrapForUse(final Object o, final Actor owner,
      final Map<SAbstractObject, SAbstractObject> transferedObjects) {
    VM.thisMethodNeedsToBeOptimized("This should probably be optimized");

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
      return orgProm.getChainedPromiseFor(this);
    } else if (!IsValue.isObjectValue(o)) {
      // Corresponds to TransferObject.isTransferObject()
      if ((o instanceof SObject && ((SObject) o).getSOMClass().isTransferObject())) {
        return TransferObject.transfer((SObject) o, owner, this,
            transferedObjects);
      } else if (o instanceof STransferArray) {
        return TransferObject.transfer((STransferArray) o, owner, this,
            transferedObjects);
      } else if (o instanceof SObjectWithoutFields && ((SObjectWithoutFields) o).getSOMClass().isTransferObject()) {
        return TransferObject.transfer((SObjectWithoutFields) o, owner, this,
            transferedObjects);
      } else {
        return new SFarReference(owner, o);
      }
    }
    return o;
  }

  protected void logMessageAddedToMailbox(final EventualMessage msg) { }
  protected void logMessageBeingExecuted(final EventualMessage msg) { }
  protected void logNoTaskForActor() { }

  /**
   * Send the give message to the actor.
   *
   * This is the main method to be used in this API.
   */
  @TruffleBoundary
  public final synchronized void send(final EventualMessage msg) {
    assert msg.getTarget() == this;
    mailbox.add(msg);
    logMessageAddedToMailbox(msg);

    if (!isExecuting) {
      isExecuting = true;
      executeOnPool();
    }
  }

  /**
   * WARNING: This method should only be called from the main thread.
   * It expects the main thread to stop executing the actor's messages and
   * will schedule all coming messages on the normal pool.
   */
  public final synchronized void relinuqishMainThreadAndMoveExecutionToPool() {
    assert isExecuting;
    if (mailbox.size() > 0) {
      executeOnPool();
    } else {
      isExecuting = false;
    }
  }

  /**
   * Is scheduled on the fork/join pool and executes messages for a specific
   * actor.
   */
  private static final class ExecAllMessages implements Runnable {
    private final Actor actor;
    private ArrayList<EventualMessage> current;
    private ArrayList<EventualMessage> emptyUnused;

    public ExecAllMessages(final Actor actor) {
      this.actor = actor;
    }

    @Override
    public void run() {
      ActorProcessingThread t = (ActorProcessingThread) Thread.currentThread();
      t.currentlyExecutingActor = actor;

      // grab current mailbox from actor
      if (emptyUnused == null) {
        emptyUnused = new ArrayList<>();
      }

      while (getCurrentMessagesOrCompleteExecution()) {
        processCurrentMessages();
      }

      t.currentlyExecutingActor = null;
    }

    private void processCurrentMessages() {
      int size = current.size();
      for (int i = 0; i < size; i++) {
        EventualMessage msg = current.get(i);
        actor.logMessageBeingExecuted(msg);
        msg.execute();
      }
      current.clear();
      emptyUnused = current;
    }

    private boolean getCurrentMessagesOrCompleteExecution() {
      synchronized (actor) {
        assert actor.isExecuting;
        current = actor.mailbox;
        if (current.isEmpty()) {
          // complete execution after all messages are processed
          actor.isExecuting = false;
          return false;
        }
        actor.mailbox = emptyUnused;
      }
      return true;
    }
  }

  @TruffleBoundary
  private void executeOnPool() {
    actorPool.execute(executor);
  }

  /**
   * @return true, if there are no scheduled submissions,
   *         and no active threads in the pool, false otherwise.
   *         This is only best effort, it does not look at the actor's
   *         message queues.
   */
  public static boolean isPoolIdle() {
    return !actorPool.hasQueuedSubmissions() && actorPool.getActiveThreadCount() == 0;
  }

  private static final class ActorProcessingThreadFactor implements ForkJoinWorkerThreadFactory {
    @Override
    public ForkJoinWorkerThread newThread(final ForkJoinPool pool) {
      return new ActorProcessingThread(pool);
    }
  }

  public static final class ActorProcessingThread extends ForkJoinWorkerThread {
    protected Actor currentlyExecutingActor;

    protected ActorProcessingThread(final ForkJoinPool pool) {
      super(pool);
    }
  }

  private static final ForkJoinPool actorPool = new ForkJoinPool(
      VmSettings.NUM_THREADS,
      new ActorProcessingThreadFactor(), null, true);

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
