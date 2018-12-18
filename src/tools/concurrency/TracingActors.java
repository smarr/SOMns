package tools.concurrency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.Output;
import som.VM;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.SPromise.STracingPromise;
import som.vm.VmSettings;
import tools.concurrency.TraceParser.ExternalMessageRecord;
import tools.concurrency.TraceParser.ExternalPromiseMessageRecord;
import tools.concurrency.TraceParser.MessageRecord;
import tools.concurrency.TraceParser.PromiseMessageRecord;
import tools.debugger.WebDebugger;
import tools.replay.actors.ExternalMessage;
import tools.replay.nodes.TraceActorContextNode;
import tools.snapshot.SnapshotRecord;
import tools.snapshot.deserialization.DeserializationBuffer;


public class TracingActors {
  public static class TracingActor extends Actor {
    private static final AtomicInteger IdGen = new AtomicInteger(0);
    protected final int                actorId;
    protected short                    ordering;
    protected int                      nextDataID;

    @CompilationFinal protected SnapshotRecord snapshotRecord;

    /**
     * Flag that indicates if a step-to-next-turn action has been made in the previous message.
     */
    protected boolean stepToNextTurn;

    public TracingActor(final VM vm) {
      super(vm);
      this.actorId = IdGen.getAndIncrement();
      if (VmSettings.SNAPSHOTS_ENABLED) {
        snapshotRecord = new SnapshotRecord(this);
      }
    }

    protected TracingActor(final VM vm, final int id) {
      super(vm);
      this.actorId = id;
    }

    public final int getActorId() {
      return actorId;
    }

    public short getOrdering() {
      return ordering++;
    }

    public synchronized int getDataId() {
      return nextDataID++;
    }

    public synchronized int peekDataId() {
      return nextDataID;
    }

    public TraceActorContextNode getActorContextNode() {
      return this.executor.getActorContextNode();
    }

    public boolean isStepToNextTurn() {
      return stepToNextTurn;
    }

    public SnapshotRecord getSnapshotRecord() {
      assert VmSettings.SNAPSHOTS_ENABLED;
      return snapshotRecord;
    }

    /**
     * For testing purposes.
     */
    public void replaceSnapshotRecord() {
      this.snapshotRecord = new SnapshotRecord(this);
    }

    @Override
    public void setStepToNextTurn(final boolean stepToNextTurn) {
      this.stepToNextTurn = stepToNextTurn;
    }

    public static void handleBreakpointsAndStepping(final EventualMessage msg,
        final WebDebugger dbg, final Actor actor) {
      if (msg.getHaltOnReceive() || ((TracingActor) actor).isStepToNextTurn()) {
        dbg.prepareSteppingUntilNextRootNode(Thread.currentThread());
        if (((TracingActor) actor).isStepToNextTurn()) { // reset flag
          actor.setStepToNextTurn(false);
        }
      }

      // check if a step-return-from-turn-to-promise-resolution has been triggered
      if (msg.getHaltOnPromiseMessageResolution()) {
        dbg.prepareSteppingUntilNextRootNode(Thread.currentThread());
      }
    }

    /**
     * To be Overrriden by ReplayActor.
     *
     * @return null
     */
    public DeserializationBuffer getDeserializationBuffer() {
      return null;
    }
  }

  public static final class ReplayActor extends TracingActor {
    protected int                              children;
    protected final Queue<MessageRecord>       expectedMessages;
    protected final ArrayList<EventualMessage> leftovers = new ArrayList<>();
    private static Map<Integer, ReplayActor>   actorList;
    private BiConsumer<Short, Integer>         dataSource;
    private int                                traceBufferId;
    private final long                         activityId;

    static {
      if (VmSettings.REPLAY) {
        actorList = new HashMap<>();
      }
    }

    public BiConsumer<Short, Integer> getDataSource() {
      assert dataSource != null;
      return dataSource;
    }

    public void setDataSource(final BiConsumer<Short, Integer> ds) {
      if (dataSource != null) {
        throw new UnsupportedOperationException("Allready has a datasource!");
      }
      dataSource = ds;
    }

    @Override
    public int getNextTraceBufferId() {
      return traceBufferId++;
    }

    private static int lookupId() {
      if (VmSettings.REPLAY && Thread.currentThread() instanceof ActorProcessingThread) {
        ActorProcessingThread t = (ActorProcessingThread) Thread.currentThread();
        ReplayActor parent = (ReplayActor) t.currentMessage.getTarget();
        int parentId = parent.getActorId();
        int childNo = parent.addChild();
        return TraceParser.getReplayId(parentId, childNo);
      }

      return 0;
    }

    public static ReplayActor getActorWithId(final int id) {
      return actorList.get(id);
    }

    @Override
    public long getId() {
      return activityId;
    }

    @TruffleBoundary
    public ReplayActor(final VM vm) {
      this(vm, lookupId());
    }

    public ReplayActor(final VM vm, final int id) {
      super(vm, id);

      this.activityId = TracingActivityThread.newEntityId();

      if (VmSettings.REPLAY) {
        expectedMessages = TraceParser.getExpectedMessages(actorId);

        synchronized (actorList) {
          actorList.put(actorId, this);
        }
      } else {
        expectedMessages = null;
      }
    }

    @Override
    protected ExecAllMessages createExecutor(final VM vm) {
      if (VmSettings.REPLAY) {
        return new ExecAllMessagesReplay(this, vm);
      } else {
        return super.createExecutor(vm);
      }
    }

    @Override
    @TruffleBoundary
    public synchronized void send(final EventualMessage msg, final ForkJoinPool actorPool) {
      assert msg.getTarget() == this;

      if (!VmSettings.REPLAY) {
        super.send(msg, actorPool);
        return;
      }

      if (firstMessage == null) {
        firstMessage = msg;
      } else {
        appendToMailbox(msg);
      }

      // actor remains dormant until the expected message arrives
      if ((!this.isExecuting) && this.replayCanProcess(msg)) {
        isExecuting = true;
        execute(actorPool);
      }
    }

    public static void scheduleAllActors(final ForkJoinPool actorPool) {
      for (ReplayActor ra : actorList.values()) {
        ra.executeIfNecessarry(actorPool);
      }
    }

    /**
     * Prints a list of expected Messages and remaining mailbox content.
     *
     * @return true if there are actors expecting messages, false otherwise.
     */
    public static boolean printMissingMessages() {
      if (!(VmSettings.REPLAY && VmSettings.DEBUG_MODE)) {
        return false;
      }

      boolean result = false;
      for (ReplayActor a : actorList.values()) {
        ReplayActor ra = a;
        if (ra.expectedMessages != null && ra.expectedMessages.peek() != null) {
          result = true; // program did not execute all messages
          Output.println("===========================================");
          Output.println("Actor " + ra.getActorId());
          Output.println("Expected: ");
          printMsg(ra.expectedMessages.peek());

          Output.println("Mailbox: ");
          if (a.firstMessage != null) {
            printMsg(a.firstMessage);
            if (a.mailboxExtension != null) {
              for (EventualMessage em : a.mailboxExtension) {
                printMsg(em);
              }
            }
          }

          for (EventualMessage em : a.leftovers) {
            printMsg(em);
          }
        } else if (a.firstMessage != null || a.mailboxExtension != null) {

          int n = a.firstMessage != null ? 1 : 0;
          n += a.mailboxExtension != null ? a.mailboxExtension.size() : 0;

          Output.println(
              a.getName() + " [" + a.getId() + "] has " + n + " unexpected messages:");
          if (a.firstMessage != null) {
            printMsg(a.firstMessage);
            if (a.mailboxExtension != null) {
              for (EventualMessage em : a.mailboxExtension) {
                printMsg(em);
              }
            }
          }
        }
      }
      return result;
    }

    private static void printMsg(final EventualMessage msg) {
      Output.print("\t");
      if (msg instanceof ExternalMessage) {
        Output.print("external ");
      }

      if (msg instanceof PromiseMessage) {
        Output.println("PromiseMessage " + msg.getSelector()
            + " from " + ((TracingActor) msg.getSender()).getActorId() + " PID "
            + ((STracingPromise) ((PromiseMessage) msg).getPromise()).getResolvingActor());
      } else {
        Output.println(
            "Message" + msg.getSelector() + " from "
                + ((TracingActor) msg.getSender()).getActorId());
      }
    }

    private static void printMsg(final MessageRecord msg) {
      Output.print("\t");
      if (msg.isExternal()) {
        Output.print("external ");
      }

      if (msg instanceof PromiseMessageRecord) {
        Output.println("PromiseMessage "
            + " from " + msg.sender + " PID "
            + ((PromiseMessageRecord) msg).pId);
      } else {
        Output.println("Message" + " from " + msg.sender);
      }
    }

    protected boolean replayCanProcess(final EventualMessage msg) {
      if (!VmSettings.REPLAY) {
        return true;
      }

      assert expectedMessages != null;

      if (expectedMessages.size() == 0) {
        // actor no longer executes messages
        return false;
      }

      MessageRecord other = expectedMessages.peek();

      if ((msg instanceof PromiseMessage) != (other instanceof TraceParser.PromiseMessageRecord)) {
        return false;
      }

      // handle promise messages
      if (msg instanceof PromiseMessage
          && (((STracingPromise) ((PromiseMessage) msg).getPromise()).getResolvingActor() != ((TraceParser.PromiseMessageRecord) other).pId)) {
        return false;
      }

      return ((ReplayActor) msg.getSender()).getActorId() == other.sender;
    }

    protected int addChild() {
      return children++;
    }

    private static void removeFirstExpectedMessage(final ReplayActor a) {
      MessageRecord first = a.expectedMessages.peek();
      MessageRecord removed = a.expectedMessages.remove();

      if (a.expectedMessages.peek() != null && a.expectedMessages.peek().isExternal()) {
        if (a.expectedMessages.peek() instanceof ExternalMessageRecord) {
          ExternalMessageRecord emr = (ExternalMessageRecord) a.expectedMessages.peek();
          actorList.get(emr.sender).getDataSource().accept(emr.method,
              emr.dataId);
        } else {
          ExternalPromiseMessageRecord emr =
              (ExternalPromiseMessageRecord) a.expectedMessages.peek();
          actorList.get(emr.pId).getDataSource().accept(emr.method,
              emr.dataId);
        }
      }
      assert first == removed;
    }

    private static class ExecAllMessagesReplay extends ExecAllMessages {
      ExecAllMessagesReplay(final Actor actor, final VM vm) {
        super(actor, vm);
      }

      private Queue<EventualMessage> determineNextMessages(
          final List<EventualMessage> postponedMsgs) {
        final ReplayActor a = (ReplayActor) actor;
        int numReceivedMsgs = 1 + (mailboxExtension == null ? 0 : mailboxExtension.size());
        numReceivedMsgs += postponedMsgs.size();

        Queue<EventualMessage> todo = new LinkedList<>();

        if (a.replayCanProcess(firstMessage)) {
          todo.add(firstMessage);
          removeFirstExpectedMessage(a);
        } else {
          postponedMsgs.add(firstMessage);
        }

        if (mailboxExtension != null) {
          for (EventualMessage msg : mailboxExtension) {
            postponedMsgs.add(msg);
          }
        }

        boolean foundNextMessage = true;
        while (foundNextMessage) {
          foundNextMessage = false;
          for (EventualMessage msg : postponedMsgs) {
            if (a.replayCanProcess(msg)) {
              todo.add(msg);
              removeFirstExpectedMessage(a);
              postponedMsgs.remove(msg);
              foundNextMessage = true;
              break;
            }
          }
        }

        assert todo.size()
            + postponedMsgs.size() == numReceivedMsgs : "We shouldn't lose any messages here.";
        return todo;
      }

      @Override
      protected void processCurrentMessages(final ActorProcessingThread currentThread,
          final WebDebugger dbg) {
        assert actor instanceof ReplayActor;
        assert size > 0;
        final ReplayActor a = (ReplayActor) actor;

        Queue<EventualMessage> todo = determineNextMessages(a.leftovers);

        for (EventualMessage msg : todo) {
          currentThread.currentMessage = msg;
          handleBreakpointsAndStepping(firstMessage, dbg, a);
          msg.execute();
        }

        currentThread.createdMessages += todo.size();
      }
    }
  }
}
