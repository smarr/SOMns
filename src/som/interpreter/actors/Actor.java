package som.interpreter.actors;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.debug.Debugger;

import som.VM;
import som.VmSettings;
import som.primitives.ObjectPrims.IsValue;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray.STransferArray;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import tools.ObjectBuffer;
import tools.actors.ActorExecutionTrace;


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

  public static Actor createActor() {
    if (VmSettings.DEBUG_MODE) {
      return new DebugActor();
    } else {
      return new Actor();
    }
  }

  public static void traceActorsExceptMainOne(final SFarReference actorFarRef) {
    Thread current = Thread.currentThread();
    if (current instanceof ActorProcessingThread) {
      ActorProcessingThread t = (ActorProcessingThread) current;
      t.createdActors.append(actorFarRef);
    }
  }

  /** Buffer for incoming messages. */
  private ObjectBuffer<EventualMessage> mailbox = new ObjectBuffer<>(16);

  /** Flag to indicate whether there is currently a F/J task executing. */
  private boolean isExecuting;

  /** Is scheduled on the pool, and executes messages to this actor. */
  private final ExecAllMessages executor;

  protected Actor() {
    isExecuting = false;
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
    mailbox.append(msg);
    logMessageAddedToMailbox(msg);

    if (!isExecuting) {
      isExecuting = true;
      executeOnPool();
    }
  }

  /**
   * Is scheduled on the fork/join pool and executes messages for a specific
   * actor.
   */
  private static final class ExecAllMessages implements Runnable {
    private final Actor actor;
    private ObjectBuffer<EventualMessage> current;

    ExecAllMessages(final Actor actor) {
      this.actor = actor;
    }

    @Override
    public void run() {
      ActorProcessingThread t = (ActorProcessingThread) Thread.currentThread();
      Debugger dbg = null;
      if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
        dbg = VM.getDebugger();
        assert dbg != null;
      }

      t.currentlyExecutingActor = actor;

      while (getCurrentMessagesOrCompleteExecution()) {
        processCurrentMessages(t, dbg);
      }

      t.currentlyExecutingActor = null;
    }

    private void processCurrentMessages(final ActorProcessingThread currentThread, final Debugger dbg) {
      for (EventualMessage msg : current) {
        actor.logMessageBeingExecuted(msg);
        currentThread.currentMessage = msg;

        if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
          dbg.executionStarted(-1, msg.getTargetSourceSection().getSource());
        }

        msg.execute();

        if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
          dbg.executionEnded();
        }
      }
      if (VmSettings.ACTOR_TRACING) {
        currentThread.processedMessages.append(current);
      }
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
        actor.mailbox = new ObjectBuffer<>(actor.mailbox.size());
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
    // TODO: this is not working when a thread blocks, then it seems
    //       not to be considered running
    return actorPool.isQuiescent();
  }

  private static final class ActorProcessingThreadFactor implements ForkJoinWorkerThreadFactory {
    @Override
    public ForkJoinWorkerThread newThread(final ForkJoinPool pool) {
      return new ActorProcessingThread(pool);
    }
  }

  public static final class ActorProcessingThread extends ForkJoinWorkerThread {
    public EventualMessage currentMessage;
    protected Actor currentlyExecutingActor;
    ObjectBuffer<SFarReference> createdActors;
    ObjectBuffer<ObjectBuffer<EventualMessage>> processedMessages;


    protected ActorProcessingThread(final ForkJoinPool pool) {
      super(pool);

      createdActors = ActorExecutionTrace.createActorBuffer();
      processedMessages = ActorExecutionTrace.createProcessedMessagesBuffer();
    }

    @Override
    public void run() {
      // TODO: figure out whether everything still works without this hack
      // Accessor.initializeThreadForUseWithPolglotEngine(VM.getEngine());

      super.run();
    }
  }

  /**
   * In case an actor processing thread terminates, provide some info.
   */
  private static final class UncaughtExceptions implements UncaughtExceptionHandler {
    @Override
    public void uncaughtException(final Thread t, final Throwable e) {
      ActorProcessingThread thread = (ActorProcessingThread) t;
      VM.errorPrintln("Processing of eventual message failed for actor: "
          + thread.currentlyExecutingActor.toString());
      e.printStackTrace();
    }
  }

  private static final ForkJoinPool actorPool = new ForkJoinPool(
      VmSettings.NUM_THREADS, new ActorProcessingThreadFactor(),
      new UncaughtExceptions(), true);

  @Override
  public String toString() {
    return "Actor";
  }

  public static final class DebugActor extends Actor {
    // TODO: remove this tracking, the new one should be more efficient
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
