package som.interpreter.actors;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import som.VM;
import som.interpreter.SomLanguage;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.vm.Activity;
import som.vm.VmSettings;
import tools.ObjectBuffer;
import tools.concurrency.KomposTrace;
import tools.concurrency.TracingActivityThread;
import tools.concurrency.TracingActors.ReplayActor;
import tools.concurrency.TracingActors.TracingActor;
import tools.debugger.WebDebugger;
import tools.debugger.entities.ActivityType;
import tools.dym.DynamicMetrics;
import tools.replay.TraceRecord;
import tools.replay.actors.UniformExecutionTrace;
import tools.replay.nodes.RecordEventNodes.RecordOneEvent;
import tools.replay.nodes.TraceContextNode;
import tools.replay.nodes.TraceContextNodeGen;
import tools.snapshot.SnapshotBuffer;


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
 * - enqueue message in actor queue
 * - execution is done by a special ExecAllMessages task
 * + - this task is submitted to the f/j pool
 * + - once it is executing, it goes to the actor,
 * + - grabs the current mailbox
 * + - and sequentially executes all messages
 */
public class Actor implements Activity {
  public static final AtomicLong numActors = DynamicMetrics.createLong("Num.Actors");

  @CompilationFinal public static RootCallTarget executorRoot;

  public static void initializeActorSystem(final SomLanguage lang) {
    ExecutorRootNode root = new ExecutorRootNode(lang);
    executorRoot = root.getCallTarget();
  }

  public static Actor createActor(final VM vm) {
    if (VmSettings.DYNAMIC_METRICS) {
      numActors.getAndIncrement();
    }

    if (VmSettings.REPLAY || VmSettings.KOMPOS_TRACING) {
      return new ReplayActor(vm);
    } else if (VmSettings.UNIFORM_TRACING) {
      return new TracingActor(vm);
    } else {
      return new Actor(vm);
    }
  }

  private static final int MAILBOX_EXTENSION_SIZE = 8;

  /**
   * Buffer for incoming messages.
   * Optimized for cases where the mailbox contains only one message.
   * Further messages are stored in moreMessages, which is initialized lazily.
   */
  protected EventualMessage               firstMessage;
  protected ObjectBuffer<EventualMessage> mailboxExtension;

  /** Flag to indicate whether there is currently a F/J task executing. */
  protected boolean isExecuting;

  /** Is scheduled on the pool, and executes messages to this actor. */
  protected final ExecAllMessages executor;

  /**
   * Possible roles for an actor.
   */
  public enum Role {
    SENDER,
    RECEIVER
  }

  protected Actor(final VM vm) {
    isExecuting = false;
    executor = createExecutor(vm);
  }

  @Override
  public ActivityType getType() {
    return ActivityType.ACTOR;
  }

  protected ExecAllMessages createExecutor(final VM vm) {
    return new ExecAllMessages(this, vm);
  }

  @Override
  public void setStepToJoin(final boolean val) {
    throw new UnsupportedOperationException(
        "Return from activity, and step to join are not supported " +
            "for event-loop actors. This code should never be reached.");
  }

  /**
   * Send the give message to the actor.
   *
   * This is the main method to be used in this API.
   */
  @TruffleBoundary
  public synchronized void send(final EventualMessage msg,
      final ForkJoinPool actorPool) {
    doSend(msg, actorPool);
  }

  public synchronized void sendInitialStartMessage(final EventualMessage msg,
      final ForkJoinPool pool) {
    doSend(msg, pool);
  }

  private void doSend(final EventualMessage msg,
      final ForkJoinPool actorPool) {
    assert msg.getTarget() == this;

    assert msg.args[msg.args.length - 1] != null
        || !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE;

    if (firstMessage == null) {
      firstMessage = msg;
    } else {
      appendToMailbox(msg);
    }

    if (!isExecuting) {
      isExecuting = true;
      execute(actorPool);
    }
  }

  @TruffleBoundary
  protected void appendToMailbox(final EventualMessage msg) {
    if (mailboxExtension == null) {
      mailboxExtension = new ObjectBuffer<>(MAILBOX_EXTENSION_SIZE);
    }
    mailboxExtension.append(msg);
  }

  public static final class ExecutorRootNode extends RootNode {

    @Child protected RecordOneEvent recordPromiseChaining;

    private ExecutorRootNode(final SomLanguage language) {
      super(language);

      if (VmSettings.SENDER_SIDE_TRACING) {
        this.recordPromiseChaining = new RecordOneEvent(TraceRecord.PROMISE_CHAINED);
      }
    }

    @Override
    public Object execute(final VirtualFrame frame) {
      ExecAllMessages executor = (ExecAllMessages) frame.getArguments()[0];
      executor.doRun();
      return null;
    }
  }

  private static final AtomicLong numTurns = DynamicMetrics.createLong("Num.Turns");

  private static final AtomicLong numTurnBatches =
      DynamicMetrics.createLong("Num.Turn.Batches");

  /**
   * Is scheduled on the fork/join pool and executes messages for a specific
   * actor.
   */
  public static class ExecAllMessages implements Runnable {
    protected final Actor actor;
    protected final VM    vm;

    protected EventualMessage               firstMessage;
    protected ObjectBuffer<EventualMessage> mailboxExtension;

    protected int size = 0;

    protected ExecAllMessages(final Actor actor, final VM vm) {
      this.actor = actor;
      this.vm = vm;
    }

    private static final TraceContextNode tracer = TraceContextNodeGen.create();

    @Override
    public void run() {
      assert executorRoot != null
          : "Actor system not initalized, call to initializeActorSystem(.) missing?";
      executorRoot.call(this);
    }

    void doRun() {
      if (VmSettings.DYNAMIC_METRICS) {
        numTurnBatches.incrementAndGet();
      }
      ObjectTransitionSafepoint.INSTANCE.register();

      ActorProcessingThread t = (ActorProcessingThread) Thread.currentThread();
      try {
        doRunWithObjectSafepoints(t);
      } finally {
        ObjectTransitionSafepoint.INSTANCE.unregister();

        if (VmSettings.UNIFORM_TRACING || VmSettings.KOMPOS_TRACING) {
          t.swapTracingBufferIfRequestedUnsync();
        }
        t.currentlyExecutingActor = null;
      }
    }

    private void doRunWithObjectSafepoints(final ActorProcessingThread t) {
      WebDebugger dbg = null;
      if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
        dbg = vm.getWebDebugger();
        assert dbg != null;
      }

      t.currentlyExecutingActor = actor;

      if (VmSettings.UNIFORM_TRACING) {
        UniformExecutionTrace.recordActivityContext(actor, tracer);
      } else if (VmSettings.KOMPOS_TRACING) {
        KomposTrace.currentActivity(actor);
      }

      while (getCurrentMessagesOrCompleteExecution()) {
        processCurrentMessages(t, dbg);
      }
    }

    protected void processCurrentMessages(final ActorProcessingThread currentThread,
        final WebDebugger dbg) {
      assert size > 0;

      if (VmSettings.SNAPSHOTS_ENABLED && !VmSettings.TEST_SNAPSHOTS) {
        SnapshotBuffer sb = currentThread.getSnapshotBuffer();
        sb.getRecord().handleTodos(sb);
        firstMessage.serialize(sb);
      }
      execute(firstMessage, currentThread, dbg);

      if (size > 1) {
        for (EventualMessage msg : mailboxExtension) {
          if (VmSettings.SNAPSHOTS_ENABLED && !VmSettings.TEST_SNAPSHOTS) {
            msg.serialize(currentThread.getSnapshotBuffer());
          }
          execute(msg, currentThread, dbg);
        }
      }
    }

    private void execute(final EventualMessage msg,
        final ActorProcessingThread currentThread, final WebDebugger dbg) {
      currentThread.currentMessage = msg;
      if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
        TracingActor.handleBreakpointsAndStepping(msg, dbg, actor);
      }

      if (VmSettings.DYNAMIC_METRICS) {
        numTurns.incrementAndGet();
      }
      msg.execute();
    }

    private boolean getCurrentMessagesOrCompleteExecution() {
      synchronized (actor) {
        assert actor.isExecuting;
        firstMessage = actor.firstMessage;
        mailboxExtension = actor.mailboxExtension;

        if (firstMessage == null) {
          assert mailboxExtension == null;
          // complete execution after all messages are processed
          actor.isExecuting = false;
          if (VmSettings.KOMPOS_TRACING) {
            KomposTrace.clearCurrentActivity(actor);
          }
          size = 0;
          return false;
        } else {
          size = 1 + ((mailboxExtension == null) ? 0 : mailboxExtension.size());
        }

        actor.firstMessage = null;
        actor.mailboxExtension = null;
      }

      return true;
    }
  }

  @TruffleBoundary
  protected void execute(final ForkJoinPool actorPool) {
    try {
      actorPool.execute(executor);
    } catch (RejectedExecutionException e) {
      throw new ThreadDeath();
    }
  }

  @Override
  public void setStepToNextTurn(final boolean val) {}

  public static final class ActorProcessingThreadFactory
      implements ForkJoinWorkerThreadFactory {

    private final VM vm;

    public ActorProcessingThreadFactory(final VM vm) {
      this.vm = vm;
    }

    @Override
    public ForkJoinWorkerThread newThread(final ForkJoinPool pool) {
      return new ActorProcessingThread(pool, vm);
    }
  }

  public static final class ActorProcessingThread extends TracingActivityThread {

    public EventualMessage currentMessage;

    protected Actor currentlyExecutingActor;

    protected ActorProcessingThread(final ForkJoinPool pool, final VM vm) {
      super(pool, vm);
    }

    @Override
    public Activity getActivity() {
      if (currentMessage == null) {
        return null;
      }
      return currentMessage.getTarget();
    }

    public Actor getCurrentActor() {
      return currentlyExecutingActor;
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
}
