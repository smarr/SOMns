package tools.concurrency;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiConsumer;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.VM;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.vm.VmSettings;
import tools.debugger.WebDebugger;
import tools.replay.PassiveEntityWithEvents;
import tools.replay.ReplayRecord;
import tools.replay.TraceParser;
import tools.replay.TraceRecord;
import tools.snapshot.SnapshotRecord;
import tools.snapshot.deserialization.DeserializationBuffer;


public class TracingActors {
  public static class TracingActor extends Actor {
    protected final long     activityId;
    protected int            nextDataID;
    protected SnapshotRecord snapshotRecord;
    private int              traceBufferId;
    protected int            version;
    private long             nextPMsgNumber;

    /**
     * Flag that indicates if a step-to-next-turn action has been made in the previous message.
     */
    protected boolean stepToNextTurn;

    public TracingActor(final VM vm) {
      super(vm);
      this.activityId = TracingActivityThread.newEntityId();
      this.version = 0;
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
    @TruffleBoundary
    public synchronized void send(final EventualMessage msg,
        final ForkJoinPool actorPool) {
      super.send(msg, actorPool);
      if (VmSettings.SENDER_SIDE_TRACING) {
        msg.getTracingNode().record(this.version);
        this.version++;
        // TODO maybe try to get the recording itself done outside the synchronized method
      }

    }

    @Override
    public synchronized void sendInitialStartMessage(final EventualMessage msg,
        final ForkJoinPool pool) {
      super.sendInitialStartMessage(msg, pool);

      if (VmSettings.SENDER_SIDE_TRACING) {
        this.version++;
      }
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

    /**
     * Only to be used by the thread executing this actor
     *
     * @return
     */
    public long getNextPromiseMsgNumber() {
      return nextPMsgNumber++;
    }
  }

  public static final class ReplayActor extends TracingActor
      implements PassiveEntityWithEvents {
    protected int                                  children;
    private final LinkedList<ReplayRecord>         replayEvents;
    protected final PriorityQueue<EventualMessage> orderedMessages =
        new PriorityQueue<>(new MessageComparator());
    private static Map<Long, ReplayActor>          actorList;
    private BiConsumer<Short, Integer>             dataSource;
    public boolean                                 poisoned        = false;
    protected final LinkedList<EventualMessage>    leftovers       = new LinkedList<>();
    private MessageRecord                          nextExpectedMessage;

    private final TraceParser traceParser;

    class MessageComparator implements Comparator<EventualMessage> {
      @Override
      public int compare(final EventualMessage o1, final EventualMessage o2) {
        return Long.compare(o1.getMessageId(), o2.getMessageId());
      }
    }

    static {
      if (VmSettings.REPLAY) {
        actorList = new WeakHashMap<>();
      }
    }

    @Override
    public TraceParser getTraceParser() {
      return traceParser;
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
    public LinkedList<ReplayRecord> getReplayEventBuffer() {
      return this.replayEvents;
    }

    public static ReplayActor getActorWithId(final long id) {
      return actorList.get(id);
    }

    @TruffleBoundary
    public ReplayActor(final VM vm) {
      super(vm);

      if (VmSettings.REPLAY) {
        replayEvents = vm.getTraceParser().getReplayEventsForEntity(activityId);

        if (VmSettings.SNAPSHOTS_ENABLED) {
          synchronized (actorList) {
            assert !actorList.containsKey(activityId);
            actorList.put(activityId, this);
          }
        }
        traceParser = vm.getTraceParser();
      } else {
        replayEvents = null;
        traceParser = null;
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
    public synchronized void send(final EventualMessage msg,
        final ForkJoinPool actorPool) {
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

      if (!this.poisoned && this.replayEvents.isEmpty()
          && this.peekNextReplayEvent() == null) {
        this.poisoned = true;
      }

      // actor remains dormant until the expected message arrives
      if ((!this.isExecuting) && this.replayCanProcess(msg) && !this.poisoned) {
        isExecuting = true;
        execute(actorPool);
      }
    }

    protected boolean replayCanProcess(final EventualMessage msg) {
      if (!VmSettings.REPLAY) {
        return true;
      }

      if (VmSettings.SENDER_SIDE_REPLAY) {
        return msg.getMessageId() == this.version;
      } else if (VmSettings.RECEIVER_SIDE_REPLAY) {
        if (nextExpectedMessage == null) {
          nextExpectedMessage = getExpected();
        }
        return replayCanProcess(nextExpectedMessage, msg);
      } else {
        assert false : "unreachable";
        return false;
      }
    }

    private MessageRecord getExpected() {
      ReplayRecord rr = this.peekNextReplayEvent();
      assert rr != null;
      assert rr.type == TraceRecord.MESSAGE || rr.type == TraceRecord.PROMISE_MESSAGE : "was "
          + rr.type;

      if (rr.type == TraceRecord.PROMISE_MESSAGE) {
        this.getNextReplayEvent();
        ReplayRecord r2 = this.getNextReplayEvent();
        return new MessageRecord(r2.eventNo, rr.eventNo);
      } else {
        this.getNextReplayEvent();
        return new MessageRecord(rr.eventNo);
      }
    }

    protected boolean replayCanProcess(final MessageRecord expected,
        final EventualMessage msg) {
      if (!VmSettings.REPLAY) {
        return true;
      }

      if (expected == null) {
        // actor no longer executes messages
        return false;
      }

      if ((msg instanceof PromiseMessage) != (expected.isPromiseMessage())) {
        return false;
      }

      // handle promise messages
      if (msg instanceof PromiseMessage
          && msg.getMessageId() != expected.getMessageId()) {
        return false;
      }

      return ((ReplayActor) msg.getSender()).getId() == expected.sender;
    }

    private static class ExecAllMessagesReplay extends ExecAllMessages {
      ExecAllMessagesReplay(final Actor actor, final VM vm) {
        super(actor, vm);
      }

      private Queue<EventualMessage> determineNextMessagesSenderSide(
          final PriorityQueue<EventualMessage> orderedMessages) {
        final ReplayActor a = (ReplayActor) actor;
        int numReceivedMsgs = 1 + (mailboxExtension == null ? 0 : mailboxExtension.size());
        numReceivedMsgs += orderedMessages.size();

        Queue<EventualMessage> toProcess = new LinkedList<>();

        if (a.replayCanProcess(firstMessage)) {
          toProcess.add(firstMessage);
          a.version++;
        } else {
          orderedMessages.add(firstMessage);
        }

        if (mailboxExtension != null) {
          for (EventualMessage msg : mailboxExtension) {
            if (a.replayCanProcess(msg)) {
              toProcess.add(msg);
              a.version++;
            } else {
              orderedMessages.add(msg);
            }
          }
        }

        while (!orderedMessages.isEmpty() && a.replayCanProcess(orderedMessages.peek())) {
          EventualMessage msg = orderedMessages.poll();
          toProcess.add(msg);
          a.version++;
        }

        assert toProcess.size()
            + orderedMessages.size() == numReceivedMsgs
            : "We shouldn't lose any messages here.";
        return toProcess;
      }

      private Queue<EventualMessage> determineNextMessagesReceiverSide(
          final LinkedList<EventualMessage> postponedMsgs) {
        final ReplayActor a = (ReplayActor) actor;
        int numReceivedMsgs = 1 + (mailboxExtension == null ? 0 : mailboxExtension.size());
        numReceivedMsgs += postponedMsgs.size();

        // Output.println(
        // "" + firstMessage + a.nextExpectedMessage + firstMessage.getSender().getId());

        Queue<EventualMessage> todo = new LinkedList<>();

        if (a.nextExpectedMessage == null) {
          a.nextExpectedMessage = a.getExpected();
        }

        if (a.nextExpectedMessage == null) {
          return todo;
        }

        if (a.replayCanProcess(a.nextExpectedMessage, firstMessage)) {
          todo.add(firstMessage);
          a.nextExpectedMessage = null;
        } else {
          postponedMsgs.add(firstMessage);
        }

        if (mailboxExtension != null) {
          for (EventualMessage msg : mailboxExtension) {
            if (todo.isEmpty() && a.replayCanProcess(a.nextExpectedMessage, msg)) {
              todo.add(msg);
              a.nextExpectedMessage = null;
            } else {
              postponedMsgs.add(msg);
            }
          }
        }

        if (todo.isEmpty()) {
          EventualMessage emsg = determineNextMessagePostponed(postponedMsgs);
          if (emsg != null) {
            todo.add(emsg);
          }
          assert todo.isEmpty() : "just in case";
        }

        assert todo.size() <= 1;
        assert todo.size()
            + postponedMsgs.size() == numReceivedMsgs : "We shouldn't lose any messages here.";
        return todo;
      }

      private EventualMessage determineNextMessagePostponed(
          final LinkedList<EventualMessage> postponedMsgs) {
        final ReplayActor a = (ReplayActor) actor;

        if (a.nextExpectedMessage == null) {
          a.nextExpectedMessage = a.getExpected();
        }

        if (a.nextExpectedMessage == null) {
          return null;
        }

        Iterator<EventualMessage> iter = postponedMsgs.iterator();
        while (iter.hasNext() && a.nextExpectedMessage != null) {
          EventualMessage msg = iter.next();
          if (a.replayCanProcess(a.nextExpectedMessage, msg)) {
            iter.remove();
            a.nextExpectedMessage = null;
            return msg;
          }
        }

        return null;
      }

      @Override
      protected void processCurrentMessages(final ActorProcessingThread currentThread,
          final WebDebugger dbg) {
        assert actor instanceof ReplayActor;
        assert size > 0;

        final ReplayActor a = (ReplayActor) actor;
        Queue<EventualMessage> todo = null;

        if (VmSettings.SENDER_SIDE_REPLAY) {
          todo = determineNextMessagesSenderSide(a.orderedMessages);
        } else if (VmSettings.RECEIVER_SIDE_REPLAY) {
          todo = determineNextMessagesReceiverSide(a.leftovers);
          assert todo.size() <= 1;
        }

        for (EventualMessage msg : todo) {
          if (VmSettings.SENDER_SIDE_REPLAY) {
            if (!a.poisoned && a.replayEvents.isEmpty()
                && a.peekNextReplayEvent() == null) {
              a.poisoned = true;
              return;
            }
          }

          currentThread.currentMessage = msg;
          handleBreakpointsAndStepping(msg, dbg, a);
          msg.execute();
        }

        currentThread.createdMessages += todo.size();

        if (VmSettings.RECEIVER_SIDE_REPLAY) {

          if (!todo.isEmpty() && !a.leftovers.isEmpty()) {
            boolean more = a.nextExpectedMessage != null || !a.replayEvents.isEmpty()
                || a.peekNextReplayEvent() != null;

            while (more && !a.leftovers.isEmpty()) {
              EventualMessage msg = determineNextMessagePostponed(a.leftovers);

              if (msg == null) {
                break;
              }

              currentThread.currentMessage = msg;
              handleBreakpointsAndStepping(msg, dbg, a);
              msg.execute();

              more = a.nextExpectedMessage != null || !a.replayEvents.isEmpty()
                  || a.peekNextReplayEvent() != null;
            }
          }
        }
      }
    }

    @Override
    public int getNextEventNumber() {
      return this.version;
    }

    class MessageRecord {
      boolean isPromiseMessage;
      long    sender;
      long    resolver;

      MessageRecord(final long sender) {
        this.sender = sender;
        this.isPromiseMessage = false;
      }

      MessageRecord(final long sender, final long resolver) {
        this.sender = sender;
        this.resolver = resolver;
        this.isPromiseMessage = true;
      }

      public boolean isPromiseMessage() {
        return isPromiseMessage;
      }

      public long getSender() {
        return sender;
      }

      public long getMessageId() {
        return resolver;
      }

      @Override
      public String toString() {
        if (isPromiseMessage) {
          return "PromsieMessage from " + sender + " and " + resolver;
        } else {
          return "Message from " + sender;
        }
      }
    }
  }
}
