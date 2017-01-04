package som.interpreter.actors;

import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.VM;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.primitives.ObjectPrims.IsValue;
import som.vm.Activity;
import som.vm.ActivityThread;
import som.vm.VmSettings;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray.STransferArray;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import tools.concurrency.ActorExecutionTrace;
import tools.debugger.WebDebugger;


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
public class Actor implements Activity {

  public static Actor createActor() {
    if (VmSettings.DEBUG_MODE) {
      return new DebugActor();
    } else if (VmSettings.ACTOR_TRACING) {
      return new TracingActor();
    } else {
      return new Actor();
    }
  }

  /** Used to shift the thread id to the 8 most significant bits. */
  private static final int THREAD_ID_SHIFT = 56;

  /** Buffer for incoming messages. */
  private EventualMessage message;
  private List<EventualMessage> moreMessages;

  private long sendTimeStamp;
  private List<Long> moreSendTimeStamps;

  /** Flag to indicate whether there is currently a F/J task executing. */
  private boolean isExecuting;

  /** Is scheduled on the pool, and executes messages to this actor. */
  private final ExecAllMessages executor;

  // used to collect absolute numbers from the threads
  private static long numCreatedMessages = 0;
  private static long numCreatedActors = 0;
  private static long numCreatedPromises = 0;
  private static long numResolvedPromises = 0;

  private static ArrayList<ActorProcessingThread> threads = new ArrayList<>();

  /**
   * Possible roles for an actor.
   */
  public enum Role {
    SENDER,
    RECEIVER
  }

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

  /**
   * Send the give message to the actor.
   *
   * This is the main method to be used in this API.
   */
  @TruffleBoundary
  public synchronized void send(final EventualMessage msg) {
    assert msg.getTarget() == this;

    if (message == null) {
      message = msg;
      if (VmSettings.MESSAGE_TIMESTAMPS) {
        sendTimeStamp = System.currentTimeMillis();
      }
    } else {
      sendMoreMessages(msg);
    }

    logMessageAddedToMailbox(msg);

    if (!isExecuting) {
      isExecuting = true;
      executeOnPool();
    }
  }

  @TruffleBoundary
  private void sendMoreMessages(final EventualMessage msg) {
    if (moreMessages == null) {
      moreMessages = new ArrayList<>(2);
      moreSendTimeStamps = new ArrayList<>(2);
    }
    if (VmSettings.MESSAGE_TIMESTAMPS) {
      moreSendTimeStamps.add(System.currentTimeMillis());
    }
    moreMessages.add(msg);
  }

  protected void logMessageAddedToMailbox(final EventualMessage msg) { }
  protected void logMessageBeingExecuted(final EventualMessage msg) { }
  protected void logNoTaskForActor() { }
  public long getActorId() { return 0; }

  /**
   * Is scheduled on the fork/join pool and executes messages for a specific
   * actor.
   */
  private static final class ExecAllMessages implements Runnable {
    private final Actor actor;
    private EventualMessage current;
    private List<EventualMessage> moreCurrent;
    private long baseMessageId;
    private long sendTimeStamp;
    private List<Long> moreSendTimeStamps;
    private long[] executionTimeStamps;
    private int size = 0;

    ExecAllMessages(final Actor actor) {
      this.actor = actor;
    }

    @Override
    public void run() {
      ObjectTransitionSafepoint.INSTANCE.register();

      ActorProcessingThread t = (ActorProcessingThread) Thread.currentThread();
      WebDebugger dbg = null;
      if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
        dbg = VM.getWebDebugger();
        assert dbg != null;
      }

      t.currentlyExecutingActor = actor;

      try {
        while (getCurrentMessagesOrCompleteExecution()) {
          processCurrentMessages(t, dbg);
        }
      } finally {
        ObjectTransitionSafepoint.INSTANCE.unregister();
      }

      t.currentlyExecutingActor = null;
    }

    private void processCurrentMessages(final ActorProcessingThread currentThread, final WebDebugger dbg) {
      if (VmSettings.ACTOR_TRACING) {
        baseMessageId = currentThread.generateMessageBaseId(size);
        currentThread.currentMessageId = baseMessageId;
      }

      if (size > 0) {
        actor.logMessageBeingExecuted(current);
        currentThread.currentMessage = current;

        if (VmSettings.TRUFFLE_DEBUGGER_ENABLED && current.isBreakpoint()) {
            dbg.prepareSteppingUntilNextRootNode();
        }

        current.execute();
        if (VmSettings.ACTOR_TRACING) {
          currentThread.currentMessageId += 1;
        }

        int i = 0;
        if (size > 1) {
          for (EventualMessage msg : moreCurrent) {
            actor.logMessageBeingExecuted(msg);
            currentThread.currentMessage = msg;

            if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
              if (msg.isBreakpoint()) {
                dbg.prepareSteppingUntilNextRootNode();
              }
            }
            if (VmSettings.MESSAGE_TIMESTAMPS) {
              executionTimeStamps[i] = System.currentTimeMillis();
              i++;
            }
            msg.execute();
            if (VmSettings.ACTOR_TRACING) {
              currentThread.currentMessageId += 1;
            }
          }
        }
      }

      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.mailboxExecuted(current, moreCurrent, baseMessageId, sendTimeStamp, moreSendTimeStamps, executionTimeStamps, actor);
      }
    }

    private boolean getCurrentMessagesOrCompleteExecution() {
      synchronized (actor) {
        assert actor.isExecuting;
        current = actor.message;
        moreCurrent = actor.moreMessages;

        if (current == null) {
          // complete execution after all messages are processed
          actor.isExecuting = false;
          size = 0;
          return false;
        } else {
          size = 1 + ((moreCurrent == null) ? 0 : moreCurrent.size());
        }

        if (VmSettings.MESSAGE_TIMESTAMPS) {
          executionTimeStamps = new long[size];
          sendTimeStamp = actor.sendTimeStamp;
          moreSendTimeStamps = actor.moreSendTimeStamps;
        }

        actor.message = null;
        actor.moreMessages = null;
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
      ActorProcessingThread t = new ActorProcessingThread(pool);
      threads.add(t);
      return t;
    }
  }

  public static final class ActorProcessingThread extends ForkJoinWorkerThread implements ActivityThread {
    public EventualMessage currentMessage;
    private static AtomicInteger threadIdGen = new AtomicInteger(0);
    protected Actor currentlyExecutingActor;
    protected final long threadId;
    protected long nextActorId = 1;
    protected long nextMessageId;
    protected long nextPromiseId;
    protected long currentMessageId;
    protected ByteBuffer tracingDataBuffer;
    public long resolvedPromises;

    protected ActorProcessingThread(final ForkJoinPool pool) {
      super(pool);
      threadId = threadIdGen.getAndIncrement();
      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.swapBuffer(this);
      }
    }

    @Override
    public Activity getActivity() {
      return currentMessage.getTarget();
    }

    protected long generateActorId() {
      long result = (threadId << THREAD_ID_SHIFT) | nextActorId;
      nextActorId++;
      return result;
    }

    protected long generateMessageBaseId(final int numMessages) {
      long result = (threadId << THREAD_ID_SHIFT) | nextMessageId;
      nextMessageId += numMessages;
      return result;
    }

    protected long generatePromiseId() {
      long result = (threadId << THREAD_ID_SHIFT) | nextPromiseId;
      nextPromiseId++;
      return result;
    }

    public ByteBuffer getThreadLocalBuffer() {
      return tracingDataBuffer;
    }

    public void setThreadLocalBuffer(final ByteBuffer threadLocalBuffer) {
      this.tracingDataBuffer = threadLocalBuffer;
    }

    public long getCurrentMessageId() {
      return currentMessageId;
    }

    @Override
    protected void onTermination(final Throwable exception) {
      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.returnBuffer(this.tracingDataBuffer);
        this.tracingDataBuffer = null;
        VM.printConcurrencyEntitiesReport("[Thread " + threadId + "]\tA#" + (nextActorId - 1) + "\t\tM#" + nextMessageId + "\t\tP#" + nextPromiseId);
        numCreatedActors += nextActorId - 1;
        numCreatedMessages += nextMessageId;
        numCreatedPromises += nextPromiseId;
        numResolvedPromises += resolvedPromises;
      }
      threads.remove(this);
      super.onTermination(exception);
    }
  }

  /**
   * In case an actor processing thread terminates, provide some info.
   */
  public static final class UncaughtExceptions implements UncaughtExceptionHandler {
    @Override
    public void uncaughtException(final Thread t, final Throwable e) {
      if (e instanceof ThreadDeath) {
        // Ignore those, we already signaled an error
        return;
      }
      ActorProcessingThread thread = (ActorProcessingThread) t;
      VM.errorPrintln("Processing of eventual message failed for actor: "
          + thread.currentlyExecutingActor.toString());
      e.printStackTrace();
    }
  }

  private static final ForkJoinPool actorPool = new ForkJoinPool(
      VmSettings.NUM_THREADS, new ActorProcessingThreadFactor(),
      new UncaughtExceptions(), true);

  public static final void shutDownActorPool() {
      actorPool.shutdown();
      try {
        actorPool.awaitTermination(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      if (VmSettings.ACTOR_TRACING) {
        VM.printConcurrencyEntitiesReport("[Total]\t\tA#" + numCreatedActors + "\t\tM#" + numCreatedMessages + "\t\tP#" + numCreatedPromises);
        VM.printConcurrencyEntitiesReport("[Unresolved] " + (numCreatedPromises - numResolvedPromises));
      }
  }

  public static final void forceSwapBuffers() {
    for (ActorProcessingThread t: threads) {
      ActorExecutionTrace.swapBuffer(t);
    }
  }

  @Override
  public String getName() {
    return toString();
  }

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

  public static final class TracingActor extends Actor {
    protected final long actorId;

    public TracingActor() {
      super();
      if (Thread.currentThread() instanceof ActorProcessingThread) {
        ActorProcessingThread t = (ActorProcessingThread) Thread.currentThread();
        this.actorId = t.generateActorId();
      } else {
        actorId = 0; // main actor
      }
    }

    @Override
    public long getActorId() {
      return actorId;
    }
  }
}
