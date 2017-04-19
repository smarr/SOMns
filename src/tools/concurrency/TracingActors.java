package tools.concurrency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ForkJoinPool;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.VM;
import som.interpreter.SArguments;
import som.interpreter.SomException;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseCallbackMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.EventualMessage.PromiseSendMessage;
import som.interpreter.actors.SPromise.SReplayPromise;
import som.interpreter.actors.EventualMessage.UntracedMessage;
import som.vm.VmSettings;
import som.vmobjects.SBlock;
import som.vmobjects.SSymbol;
import tools.concurrency.TraceParser.MessageRecord;
import tools.debugger.WebDebugger;

public class TracingActors {
  public static class TracingActor extends Actor {
    protected long actorId;
    protected int mailboxNumber;
    /** Flag that indicates if a step-to-next-turn action has been made in the previous message. */
    protected boolean stepToNextTurn;

    private SSymbol actorType = null;

    private List<Assertion> activeAssertions;
    private HashMap<SSymbol, SBlock> sendHooks;
    private HashMap<SSymbol, SBlock> receiveHooks;

    public TracingActor(final VM vm) {
      super(vm);
      if (Thread.currentThread() instanceof TracingActivityThread) {
        TracingActivityThread t = (TracingActivityThread) Thread.currentThread();
        this.actorId = t.generateActivityId();
      } else {
        this.actorId = 0; // main actor
      }
    }

    public int getAndIncrementMailboxNumber() {
      return mailboxNumber++;
    }

    @Override
    public long getId() { return actorId; }

    public boolean isStepToNextTurn() {
      return stepToNextTurn;
    }

    @Override
    public void setStepToNextTurn(final boolean stepToNextTurn) {
      this.stepToNextTurn = stepToNextTurn;
    }

    public static void handleBreakpointsAndStepping(final EventualMessage msg, final WebDebugger dbg, final Actor actor) {
      if (!VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
        return;
      }

      if (msg.isMessageReceiverBreakpointSet() || ((TracingActor) actor).isStepToNextTurn()) {
        dbg.prepareSteppingUntilNextRootNode();
        if (((TracingActor) actor).isStepToNextTurn()) { // reset flag
          actor.setStepToNextTurn(false);
        }
      }

      // check if a step-return-from-turn-to-promise-resolution has been triggered
      if (msg instanceof PromiseSendMessage && ((PromiseSendMessage) msg).getPromise().isTriggerStopBeforeExecuteCallback()) {
          dbg.prepareSteppingUntilNextRootNode();
      } else if (msg instanceof PromiseCallbackMessage && ((PromiseCallbackMessage) msg).getPromiseRegisteredOn().isTriggerStopBeforeExecuteCallback()) {
          dbg.prepareSteppingUntilNextRootNode();
      }
   }
    public SSymbol getActorType() {
      return actorType;
    }

    public void setActorType(final SSymbol actorType) {
      this.actorType = actorType;
    }

    public void addAssertion(final Assertion a) {
      if (activeAssertions == null) {
        activeAssertions = new ArrayList<>();
      }

      activeAssertions.add(a);
    }

    @TruffleBoundary
    public void addSendHook(final SSymbol msg, final SBlock block) {
      if (sendHooks == null) {
        sendHooks = new HashMap<>();
      }
      sendHooks.put(msg, block);
    }

    @TruffleBoundary
    public void addReceiveHook(final SSymbol msg, final SBlock block) {
      if (receiveHooks == null) {
        receiveHooks = new HashMap<>();
      }
      receiveHooks.put(msg, block);
    }

    public void checkAssertions(final EventualMessage msg, final VM vm) {
      if (activeAssertions == null || activeAssertions.size() == 0) {
        return;
      }

      // safe way for iterating and removing elements, for each caused concurrent modification exception for no reason.
      Iterator<Assertion> iter = activeAssertions.iterator();
      while (iter.hasNext()){
        Assertion a = iter.next();
        if (!a.evaluate(this, msg, vm.getActorPool())) {
          iter.remove();
        }
      }
    }

    @TruffleBoundary
    public void checkSendHooks(final EventualMessage msg, final VM vm) {
      if (sendHooks != null) {
        if (sendHooks.containsKey(msg.getSelector())) {
          SBlock block = sendHooks.get(msg.getSelector());
          sendHooks.clear();
          if (receiveHooks != null) {
            receiveHooks.clear();
          }

          if (block.getMethod().getNumberOfArguments() > 0) {
            invokeBlock(block, msg.getArgs(), vm);
          } else {
            invokeBlock(block, vm);
          }
        } else if (sendHooks.size() > 0) {
          throw new AssertionError("sending Message: " + msg.getSelector() + " violates the message protocol!");
        }
      }
    }

    @TruffleBoundary
    public void checkReceiveHooks(final EventualMessage msg, final VM vm) {
      if (receiveHooks != null) {
        if (receiveHooks.containsKey(msg.getSelector())) {
          SBlock block = receiveHooks.get(msg.getSelector());
          receiveHooks.clear();
          if (sendHooks != null) {
            sendHooks.clear();
          }
          invokeBlock(block, msg.getArgs(), vm);
        } else  if (receiveHooks.size() > 0) {
          throw new AssertionError("receiving Message: " + msg.getSelector() + " violates the message protocol!");
        }
      }
    }

    private void invokeBlock(final SBlock block, final Object[] args, final VM vm) {
      try {
        block.getMethod().invoke(new Object[] {block, SArguments.getArgumentsWithoutReceiver(args)});
      } catch (SomException t) {
        t.printStackTrace();

        vm.errorExit("EventualMessage failed with Exception.");
      }
    }

    private void invokeBlock(final SBlock block, final VM vm) {
      try {
        block.getMethod().invoke(new Object[] {block});
      } catch (SomException t) {
        t.printStackTrace();
        vm.errorExit("EventualMessage failed with Exception.");
      }
    }
  }




  public static final class ReplayActor extends TracingActor {
    protected int children;
    protected final Queue<MessageRecord> expectedMessages;
    protected final ArrayList<EventualMessage> leftovers = new ArrayList<>();
    private static List<ReplayActor> actorList;

    static {
      if (VmSettings.DEBUG_MODE) {
        actorList = new ArrayList<>();
      }
    }

    @TruffleBoundary
    public ReplayActor(final VM vm) {
      super(vm);
      if (Thread.currentThread() instanceof ActorProcessingThread) {
        ActorProcessingThread t = (ActorProcessingThread) Thread.currentThread();
        ReplayActor parent = (ReplayActor) t.currentMessage.getTarget();
        long parentId = parent.getId();
        int childNo = parent.addChild();

        actorId = TraceParser.getReplayId(parentId, childNo);
        expectedMessages = TraceParser.getExpectedMessages(actorId);

      } else {
        expectedMessages = TraceParser.getExpectedMessages(0L);
      }

      if (VmSettings.DEBUG_MODE) {
        synchronized (actorList) { actorList.add(this); }
      }
    }

    @Override
    protected ExecAllMessages createExecutor(final VM vm) {
      return new ExecAllMessagesReplay(this, vm);
    }

    @Override
    @TruffleBoundary
    public synchronized void send(final EventualMessage msg, final ForkJoinPool actorPool) {
      assert msg.getTarget() == this;

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
     * @return true if there are actors expecting messages, false otherwise.
     */
    public static boolean printMissingMessages() {
      if (!(VmSettings.REPLAY && VmSettings.DEBUG_MODE)) {
        return false;
      }

      boolean result = false;
      for (ReplayActor a : actorList) {
        ReplayActor ra = a;
        if (ra.expectedMessages != null && ra.expectedMessages.peek() != null) {
          result = true; // program did not execute all messages
          if (ra.expectedMessages.peek() instanceof TraceParser.PromiseMessageRecord) {
            VM.println(a.getName() + " [" + ra.getId() + "] expecting PromiseMessage " + ra.expectedMessages.peek().symbol + " from " + ra.expectedMessages.peek().sender + " PID " + ((TraceParser.PromiseMessageRecord) ra.expectedMessages.peek()).pId);
          } else {
            VM.println(a.getName() + " [" + ra.getId() + "] expecting Message" + ra.expectedMessages.peek().symbol + " from " + ra.expectedMessages.peek().sender);
          }

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

          int n = a.firstMessage != null ?  1 : 0;
          n += a.mailboxExtension != null ? a.mailboxExtension.size() : 0;

          VM.println(a.getName() + " [" + a.getId() + "] has " + n + " unexpected messages");
        }
      }
      return result;
    }

    private static void printMsg(final EventualMessage msg) {
      if (msg instanceof PromiseMessage) {
        VM.println("\t" + "PromiseMessage " + msg.getSelector() + " from " + msg.getSender().getId() + " PID " + ((SReplayPromise) ((PromiseMessage) msg).getPromise()).getResolvingActor());
      } else {
        VM.println("\t" + "Message" + msg.getSelector() + " from " + msg.getSender().getId());
      }
    }

    protected boolean replayCanProcess(final EventualMessage msg) {
      if (!VmSettings.REPLAY || msg instanceof UntracedMessage) {
        return true;
      }

      assert expectedMessages != null;

      if (expectedMessages.size() == 0) {
        // actor no longer executes messages
        return false;
      }

      MessageRecord other = expectedMessages.peek();

      // handle promise messages
      if (other instanceof TraceParser.PromiseMessageRecord) {
        if (msg instanceof PromiseMessage) {
          if (((SReplayPromise) ((PromiseMessage) msg).getPromise()).getResolvingActor() != ((TraceParser.PromiseMessageRecord) other).pId) {
            return false;
          }
        } else {
          return false;
        }
      }

      assert msg.getSelector() == other.symbol || !msg.getSelector().equals(other.symbol);
      return msg.getSelector() == other.symbol && msg.getSender().getId() == other.sender;
    }

    protected int addChild() {
      return children++;
    }

    private static void removeFirstExpectedMessage(final ReplayActor a) {
      MessageRecord first = a.expectedMessages.peek();
      MessageRecord removed = a.expectedMessages.remove();
      assert first == removed;
    }

    private static class ExecAllMessagesReplay extends ExecAllMessages {
      ExecAllMessagesReplay(final Actor actor, final VM vm) {
        super(actor, vm);
      }

      private Queue<EventualMessage> determineNextMessages(final List<EventualMessage> postponedMsgs) {
        final ReplayActor a = (ReplayActor) actor;
        int numReceivedMsgs = 1 + (mailboxExtension == null ? 0 : mailboxExtension.size());
        numReceivedMsgs += postponedMsgs.size();

        Queue<EventualMessage> todo = new LinkedList<>();

        if (a.replayCanProcess(firstMessage)) {
          todo.add(firstMessage);
          if (!(firstMessage instanceof UntracedMessage)) {
            removeFirstExpectedMessage(a);
          }
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
              if (!(msg instanceof UntracedMessage)) {
                removeFirstExpectedMessage(a);
              }
              postponedMsgs.remove(msg);
              foundNextMessage = true;
              break;
            }
          }
        }

        assert todo.size() + postponedMsgs.size() == numReceivedMsgs : "We shouldn't lose any messages here.";
        return todo;
      }

      @Override
      protected void processCurrentMessages(final ActorProcessingThread currentThread, final WebDebugger dbg) {
        assert actor instanceof ReplayActor;
        assert size > 0;

        final ReplayActor a = (ReplayActor) actor;
        Queue<EventualMessage> todo = determineNextMessages(a.leftovers);

        baseMessageId = currentThread.generateMessageBaseId(todo.size());
        currentThread.currentMessageId = baseMessageId;

        for (EventualMessage msg : todo) {
          currentThread.currentMessage = msg;
          handleBreakpointsAndStepping(firstMessage, dbg, a);
          if (!(msg instanceof UntracedMessage)) {
            ((TracingActor) actor).checkReceiveHooks(msg, vm);
          }
          msg.execute();
          if (!(msg instanceof UntracedMessage)) {
            ((TracingActor) actor).checkAssertions(msg, vm);
          }

          currentThread.currentMessageId += 1;
        }

        currentThread.createdMessages += todo.size();
        ActorExecutionTrace.mailboxExecutedReplay(todo, baseMessageId, currentMailboxNo, actor);
      }
    }
  }
}
