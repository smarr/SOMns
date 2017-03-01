package som.interpreter.actors;

import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.VM;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.primitives.ObjectPrims.IsValue;
import som.vm.Activity;
import som.vm.ActivityThread;
import som.vm.VmSettings;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray.STransferArray;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import tools.ObjectBuffer;
import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.TraceParser;
import tools.concurrency.TraceParser.Message;
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
    if (VmSettings.REPLAY) {
      return new ReplayActor();
    } else if (VmSettings.ACTOR_TRACING) {
      return new TracingActor();
    } else {
      return new Actor();
    }
  }

  /** Used to shift the thread id to the 8 most significant bits. */
  private static final int THREAD_ID_SHIFT = 56;
  private static final int MAILBOX_EXTENSION_SIZE = 8;
  private static List<Actor> actorList;

  /**
   * Buffer for incoming messages.
   * Optimized for cases where the mailbox contains only one message.
   * Further messages are stored in moreMessages, which is initialized lazily.
   */
  private EventualMessage firstMessage;
  private ObjectBuffer<EventualMessage> mailboxExtension;

  private long firstMessageTimeStamp;
  private ObjectBuffer<Long> mailboxExtensionTimeStamps;

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

  static {
    if (VmSettings.REPLAY && VmSettings.DEBUG_MODE) {
      actorList = new ArrayList<>();
    } else {
      actorList = null;
    }
  }

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

  /**
   * Prints a list of expected Messages and remaining mailbox content.
   * @return true if there are actors expecting messages, false otherwise.
   */
  public static boolean printMissingMessages() {
    if (!(VmSettings.REPLAY && VmSettings.DEBUG_MODE)) {
      return false;
    }

    boolean result = false;
    for (Actor a : actorList) {
      if (a.getReplayExpectedMessages() != null && a.getReplayExpectedMessages().peek() != null) {
        result = true; // program did not execute all messages
        if (a.getReplayExpectedMessages().peek() instanceof TraceParser.PromiseMessage) {
          VM.println(a.getName() + " [" + a.getReplayActorId() + "] expecting PromiseMessage " + a.getReplayExpectedMessages().peek().symbol + " from " + a.getReplayExpectedMessages().peek().sender + " PID " + ((TraceParser.PromiseMessage) a.getReplayExpectedMessages().peek()).pId);
        } else {
          VM.println(a.getName() + " [" + a.getReplayActorId() + "] expecting Message" + a.getReplayExpectedMessages().peek().symbol + " from " + a.getReplayExpectedMessages().peek().sender);
        }

        if (a.firstMessage != null) {
          printMsg(a.firstMessage);
          if (a.mailboxExtension != null) {
            for (EventualMessage em : a.mailboxExtension) {
              printMsg(em);
            }
          }
        }

        for (EventualMessage em : ((ReplayActor) a).leftovers) {
          printMsg(em);
        }
      } else if (a.firstMessage != null || a.mailboxExtension != null) {

        int n = a.firstMessage != null ?  1 : 0;
        n += a.mailboxExtension != null ? a.mailboxExtension.size() : 0;

        VM.println(a.getName() + " [" + a.getReplayActorId() + "] has " + n + " unexpected messages");
      }
    }
    return result;
  }

  private static void printMsg(final EventualMessage msg) {
    if (msg instanceof PromiseMessage) {
      VM.println("\t" + "PromiseMessage " + msg.getSelector() + " from " + msg.getSender().getReplayActorId() + " PID " + ((PromiseMessage) msg).getPromise().getReplayPromiseId());
    } else {
      VM.println("\t" + "Message" + msg.getSelector() + " from " + msg.getSender().getReplayActorId());
    }
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

    if (firstMessage == null) {
      firstMessage = msg;
      if (VmSettings.MESSAGE_TIMESTAMPS) {
        firstMessageTimeStamp = System.currentTimeMillis();
      }
    } else {
      appendToMailbox(msg);
    }

    logMessageAddedToMailbox(msg);

    // actor remains dormant until the expected message arrives
    if ((!isExecuting) && replayCanProcess(msg)) {
      isExecuting = true;
      executeOnPool();
    }
  }

  private boolean replayCanProcess(final EventualMessage msg) {
    if (!VmSettings.REPLAY) {
      return true;
    }

    assert getReplayExpectedMessages() != null;

    if (getReplayExpectedMessages().size() == 0) {
      // actor no longer executes messages
      return false;
    }

    Message other = getReplayExpectedMessages().peek();

    // handle promise messages
    if (other instanceof TraceParser.PromiseMessage) {
      if (msg instanceof PromiseMessage) {
        return ((PromiseMessage) msg).getPromise().getReplayPromiseId() == ((TraceParser.PromiseMessage) other).pId;
      } else {
        return false;
      }
    }

    return msg.getSelector().equals(other.symbol) && msg.getSender().getReplayActorId() == other.sender;
  }

  @TruffleBoundary
  private void appendToMailbox(final EventualMessage msg) {
    if (mailboxExtension == null) {
      mailboxExtension = new ObjectBuffer<>(MAILBOX_EXTENSION_SIZE);
      mailboxExtensionTimeStamps = new ObjectBuffer<>(MAILBOX_EXTENSION_SIZE);
    }
    if (VmSettings.MESSAGE_TIMESTAMPS) {
      mailboxExtensionTimeStamps.append(System.currentTimeMillis());
    }
    mailboxExtension.append(msg);
  }

  protected void logMessageAddedToMailbox(final EventualMessage msg) { }
  protected void logMessageBeingExecuted(final EventualMessage msg) { }
  protected void logNoTaskForActor() { }
  public long getActorId() { return 0; }
  public long getReplayActorId() { return 0; }
  public int getAndIncrementMailboxNumber() { return 0; }
  protected Queue<Message> getReplayExpectedMessages() { return null; }
  protected Queue<Long> getReplayPromiseIds() { return null; }
  protected int addChild() { return 0; }

  /**
   * Is scheduled on the fork/join pool and executes messages for a specific
   * actor.
   */
  private static final class ExecAllMessages implements Runnable {
    private final Actor actor;
    private EventualMessage firstMessage;
    private ObjectBuffer<EventualMessage> mailboxExtension;
    private long baseMessageId;
    private long firstMessageTimeStamp;
    private ObjectBuffer<Long> mailboxExtensionTimeStamps;
    private long[] executionTimeStamps;
    private int currentMailboxNo;
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
          if (VmSettings.REPLAY) {
            processCurrentMessagesReplay(t, dbg);
          } else {
            processCurrentMessages(t, dbg);
          }
        }
      } finally {
        ObjectTransitionSafepoint.INSTANCE.unregister();
      }

      t.currentlyExecutingActor = null;
    }

    private void processCurrentMessagesReplay(final ActorProcessingThread currentThread, final WebDebugger dbg) {
      assert actor instanceof ReplayActor;
      assert (size > 0);

      boolean cont = true;
      Queue<EventualMessage> todo = new LinkedList<>();
      List<EventualMessage> rem = ((ReplayActor) actor).leftovers;

        if (actor.replayCanProcess(firstMessage)) {
          todo.add(firstMessage);
          if (actor.getReplayExpectedMessages().peek().createdPromises != null) {
            actor.getReplayPromiseIds().addAll(actor.getReplayExpectedMessages().peek().createdPromises);
          }
          actor.getReplayExpectedMessages().remove();
        } else {
          rem.add(firstMessage);
        }

        if (mailboxExtension != null) {
          for (EventualMessage msg: mailboxExtension) {
            rem.add(msg);
          }
        }

        while (cont) {
          cont = false;
          for (EventualMessage msg: rem) {
            if (actor.replayCanProcess(msg)) {
              todo.add(msg);
              if (actor.getReplayExpectedMessages().peek().createdPromises != null) {
                actor.getReplayPromiseIds().addAll(actor.getReplayExpectedMessages().peek().createdPromises);
              }
              actor.getReplayExpectedMessages().remove();
              rem.remove(msg);
              cont = true;
              break;
            }
          }
        }

        baseMessageId = currentThread.generateMessageBaseId(todo.size());
        currentThread.currentMessageId = baseMessageId;

        for (EventualMessage msg : todo) {
          actor.logMessageBeingExecuted(msg);
          currentThread.currentMessage = msg;

          if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
            if (msg.isBreakpoint()) {
              dbg.prepareSteppingUntilNextRootNode();
            }
          }

          msg.execute();
          currentThread.currentMessageId += 1;
        }

        currentThread.createdMessages += todo.size();
        ActorExecutionTrace.mailboxExecutedReplay(todo, baseMessageId, currentMailboxNo, actor);
    }

    private void processCurrentMessages(final ActorProcessingThread currentThread, final WebDebugger dbg) {
      if (VmSettings.ACTOR_TRACING) {
        baseMessageId = currentThread.generateMessageBaseId(size);
        currentThread.currentMessageId = baseMessageId;
      }

      assert (size > 0);
      actor.logMessageBeingExecuted(firstMessage);
      currentThread.currentMessage = firstMessage;

      if (VmSettings.TRUFFLE_DEBUGGER_ENABLED && firstMessage.isBreakpoint()) {
        dbg.prepareSteppingUntilNextRootNode();
      }

      firstMessage.execute();

      if (VmSettings.ACTOR_TRACING) {
        currentThread.currentMessageId += 1;
      }

      int i = 0;
      if (size > 1) {
        for (EventualMessage msg : mailboxExtension) {
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

      if (VmSettings.ACTOR_TRACING) {
        currentThread.createdMessages += size;
        ActorExecutionTrace.mailboxExecuted(firstMessage, mailboxExtension, baseMessageId, currentMailboxNo, firstMessageTimeStamp, mailboxExtensionTimeStamps, executionTimeStamps, actor);
      }
    }

    private boolean getCurrentMessagesOrCompleteExecution() {
      synchronized (actor) {
        assert actor.isExecuting;
        firstMessage = actor.firstMessage;
        mailboxExtension = actor.mailboxExtension;
        currentMailboxNo = actor.getAndIncrementMailboxNumber();

        if (firstMessage == null) {
          // complete execution after all messages are processed
          actor.isExecuting = false;
          size = 0;
          return false;
        } else {
          size = 1 + ((mailboxExtension == null) ? 0 : mailboxExtension.size());
        }

        if (VmSettings.MESSAGE_TIMESTAMPS) {
          executionTimeStamps = new long[size];
          firstMessageTimeStamp = actor.firstMessageTimeStamp;
          mailboxExtensionTimeStamps = actor.mailboxExtensionTimeStamps;
        }

        actor.firstMessage = null;
        actor.mailboxExtension = null;
      }

      return true;
    }
  }

  @TruffleBoundary
  private void executeOnPool() {
    try {
      actorPool.execute(executor);
    } catch (RejectedExecutionException e) {
      throw new ThreadDeath();
    }
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
    protected long createdMessages;
    protected long currentMessageId;
    protected ByteBuffer tracingDataBuffer;
    public long resolvedPromises;

    protected ActorProcessingThread(final ForkJoinPool pool) {
      super(pool);
      threadId = threadIdGen.getAndIncrement();
      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.swapBuffer(this);
        nextActorId = (threadId << THREAD_ID_SHIFT) + 1;
        nextMessageId = (threadId << THREAD_ID_SHIFT);
        nextPromiseId = (threadId << THREAD_ID_SHIFT);
      }
    }

    @Override
    public Activity getActivity() {
      return currentMessage.getTarget();
    }

    protected long generateActorId() {
      return nextActorId++;
    }

    protected long generateMessageBaseId(final int numMessages) {
      long result = nextMessageId;
      nextMessageId += numMessages;
      return result;
    }

    protected long generatePromiseId() {
      return nextPromiseId++;
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
        long createdActors = nextActorId - 1 - (threadId << THREAD_ID_SHIFT);
        long createdPromises = nextPromiseId & 0x0FFFFFFFFFFFFFFL;

        ActorExecutionTrace.returnBuffer(this.tracingDataBuffer);
        this.tracingDataBuffer = null;
        VM.printConcurrencyEntitiesReport("[Thread " + threadId + "]\tA#" + createdActors + "\t\tM#" + createdMessages + "\t\tP#" + createdPromises);
        numCreatedActors += createdActors;
        numCreatedMessages += createdMessages;
        numCreatedPromises += createdPromises;
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

  public static class TracingActor extends Actor {
    protected final long actorId;
    protected int mailboxNumber;

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
    public int getAndIncrementMailboxNumber() {
      return mailboxNumber++;
    }

    @Override
    public long getActorId() {
      return actorId;
    }
  }

  public static final class ReplayActor extends TracingActor{
    protected int children;
    protected final long replayId;
    protected final Queue<Message> expectedMessages;
    protected final ArrayList<EventualMessage> leftovers = new ArrayList<>();
    protected final Queue<Long> replayPromiseIds;

    @TruffleBoundary
    public ReplayActor() {
      super();
      if (Thread.currentThread() instanceof ActorProcessingThread) {
        ActorProcessingThread t = (ActorProcessingThread) Thread.currentThread();
        Actor parent = t.currentMessage.getTarget();
        long parentId = parent.getReplayActorId();
        int childNo = parent.addChild();

        replayId = TraceParser.getReplayId(parentId, childNo);
        expectedMessages = TraceParser.getExpectedMessages(replayId);

      } else {
        replayId = 0;
        expectedMessages = TraceParser.getExpectedMessages(0L);

      }
      replayPromiseIds = new LinkedList<>();

      if (VmSettings.DEBUG_MODE) {
        synchronized (actorList) { actorList.add(this); }
      }
    }

    @Override
    public long getReplayActorId() {
      return replayId;
    }

    @Override
    protected int addChild() {
      return children++;
    }

    @Override
    protected Queue<Message> getReplayExpectedMessages() {
      return expectedMessages;
    }

    @Override
    protected Queue<Long> getReplayPromiseIds() {
      return replayPromiseIds;
    }
  }
}
