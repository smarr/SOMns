package tools.concurrency;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiConsumer;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.Output;
import som.VM;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.SPromise.STracingPromise;
import som.vm.VmSettings;
import tools.debugger.WebDebugger;
import tools.replay.ReplayRecord;
import tools.replay.ReplayRecord.ExternalMessageRecord;
import tools.replay.ReplayRecord.ExternalPromiseMessageRecord;
import tools.replay.ReplayRecord.MessageRecord;
import tools.replay.ReplayRecord.PromiseMessageRecord;
import tools.replay.TraceParser;
import tools.replay.actors.ExternalMessage;
import tools.snapshot.SnapshotRecord;
import tools.snapshot.deserialization.DeserializationBuffer;


public class TracingActors {
  public static class TracingActor extends Actor {
    protected final long     activityId;
    protected int            nextDataID;
    protected SnapshotRecord snapshotRecord;
    private int              traceBufferId;

    /**
     * Flag that indicates if a step-to-next-turn action has been made in the previous message.
     */
    protected boolean stepToNextTurn;

    public TracingActor(final VM vm) {
      super(vm);
      this.activityId = TracingActivityThread.newEntityId();
      assert this.activityId >= 0;
      if (VmSettings.SNAPSHOTS_ENABLED) {
        snapshotRecord = new SnapshotRecord();
      }
    }

    protected TracingActor(final VM vm, final long id) {
      super(vm);
      this.activityId = id;
    }

    public final int getActorId() {
      // TODO: remove after rebasing snapshot PR
      throw new UnsupportedOperationException("Please remove this call and use getId instead");
    }

    @Override
    public long getId() {
      return activityId;
    }

    @Override
    public int getNextTraceBufferId() {
      return traceBufferId++;
    }

    @Override
    public synchronized int getDataId() {
      return nextDataID++;
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
      this.snapshotRecord = new SnapshotRecord();
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
    private final Queue<ReplayRecord>          replayEvents;
    protected final ArrayList<EventualMessage> leftovers = new ArrayList<>();
    private static Map<Long, ReplayActor>      actorList;
    private BiConsumer<Short, Integer>         dataSource;

    static {
      if (VmSettings.REPLAY) {
        actorList = new WeakHashMap<>();
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
    public ReplayRecord getNextReplayEvent() {
      return replayEvents.poll();
    }

    public static ReplayActor getActorWithId(final long id) {
      return actorList.get(id);
    }

    @TruffleBoundary
    public ReplayActor(final VM vm) {
      super(vm);

      if (VmSettings.REPLAY) {
        expectedMessages = TraceParser.getExpectedMessages(activityId);
        replayEvents = TraceParser.getReplayEventsForEntity(activityId);

        synchronized (actorList) {
          actorList.put(activityId, this);
        }
      } else {
        expectedMessages = null;
        replayEvents = null;
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
          Output.println("Actor " + ra.getId());
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
            + " from " + ((TracingActor) msg.getSender()).getId() + " PID "
            + ((STracingPromise) ((PromiseMessage) msg).getPromise()).getResolvingActor());
      } else {
        Output.println(
            "Message" + msg.getSelector() + " from "
                + ((TracingActor) msg.getSender()).getId());
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

      if ((msg instanceof PromiseMessage) != (other instanceof ReplayRecord.PromiseMessageRecord)) {
        return false;
      }

      // handle promise messages
      if (msg instanceof PromiseMessage
          && (((STracingPromise) ((PromiseMessage) msg).getPromise()).getResolvingActor() != ((ReplayRecord.PromiseMessageRecord) other).pId)) {
        return false;
      }

      return ((ReplayActor) msg.getSender()).getId() == other.sender;
    }

    @Override
    public int addChild() {
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
          handleBreakpointsAndStepping(msg, dbg, a);
          msg.execute();
        }

        currentThread.createdMessages += todo.size();
      }
    }
  }
}
