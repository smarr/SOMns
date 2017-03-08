package tools.concurrency;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.VM;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.vm.VmSettings;
import tools.concurrency.TraceParser.MessageRecord;
import tools.debugger.WebDebugger;

public class TracingActors {
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

    public int getAndIncrementMailboxNumber() {
      return mailboxNumber++;
    }

    public long getActorId() {
      return actorId;
    }
  }

  public static final class ReplayActor extends TracingActor{
    protected int children;
    protected final long replayId;
    protected final Queue<MessageRecord> expectedMessages;
    protected final ArrayList<EventualMessage> leftovers = new ArrayList<>();
    protected final Queue<Long> replayPromiseIds;
    private static List<ReplayActor> actorList;

    static {
      if (VmSettings.DEBUG_MODE) {
        actorList = new ArrayList<>();
      }
    }

    @TruffleBoundary
    public ReplayActor() {
      isExecuting = false;
      this.executor = new ExecAllMessagesReplay(this);
      if (Thread.currentThread() instanceof ActorProcessingThread) {
        ActorProcessingThread t = (ActorProcessingThread) Thread.currentThread();
        ReplayActor parent = (ReplayActor) t.currentMessage.getTarget();
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
    @TruffleBoundary
    public synchronized void send(final EventualMessage msg) {
      assert msg.getTarget() == this;

      if (this.firstMessage == null) {
        firstMessage = msg;
      } else {
        this.appendToMailbox(msg);
      }

      // actor remains dormant until the expected message arrives
      if ((!this.isExecuting) && this.replayCanProcess(msg)) {
        this.isExecuting = true;
        this.executeOnPool();
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
        if (ra.getReplayExpectedMessages() != null && ra.getReplayExpectedMessages().peek() != null) {
          result = true; // program did not execute all messages
          if (ra.getReplayExpectedMessages().peek() instanceof TraceParser.PromiseMessageRecord) {
            VM.println(a.getName() + " [" + ra.getReplayActorId() + "] expecting PromiseMessage " + ra.getReplayExpectedMessages().peek().symbol + " from " + ra.getReplayExpectedMessages().peek().sender + " PID " + ((TraceParser.PromiseMessageRecord) ra.getReplayExpectedMessages().peek()).pId);
          } else {
            VM.println(a.getName() + " [" + ra.getReplayActorId() + "] expecting Message" + ra.getReplayExpectedMessages().peek().symbol + " from " + ra.getReplayExpectedMessages().peek().sender);
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

          VM.println(a.getName() + " [" + a.getReplayActorId() + "] has " + n + " unexpected messages");
        }
      }
      return result;
    }

    private static void printMsg(final EventualMessage msg) {
      if (msg instanceof PromiseMessage) {
        VM.println("\t" + "PromiseMessage " + msg.getSelector() + " from " + ((ReplayActor) msg.getSender()).getReplayActorId() + " PID " + ((PromiseMessage) msg).getPromise().getReplayPromiseId());
      } else {
        VM.println("\t" + "Message" + msg.getSelector() + " from " + ((ReplayActor) msg.getSender()).getReplayActorId());
      }
    }

    protected boolean replayCanProcess(final EventualMessage msg) {
      if (!VmSettings.REPLAY) {
        return true;
      }

      assert getReplayExpectedMessages() != null;

      if (getReplayExpectedMessages().size() == 0) {
        // actor no longer executes messages
        return false;
      }

      MessageRecord other = getReplayExpectedMessages().peek();

      // handle promise messages
      if (other instanceof TraceParser.PromiseMessageRecord) {
        if (msg instanceof PromiseMessage) {
          return ((PromiseMessage) msg).getPromise().getReplayPromiseId() == ((TraceParser.PromiseMessageRecord) other).pId;
        } else {
          return false;
        }
      }

      return msg.getSelector().equals(other.symbol) && ((ReplayActor) msg.getSender()).getReplayActorId() == other.sender;
    }

    public long getReplayActorId() {
      return replayId;
    }

    protected int addChild() {
      return children++;
    }

    protected Queue<MessageRecord> getReplayExpectedMessages() {
      return expectedMessages;
    }

    public Queue<Long> getReplayPromiseIds() {
      return replayPromiseIds;
    }

    private static class ExecAllMessagesReplay extends ExecAllMessages {
      ExecAllMessagesReplay(final Actor actor) {
        super(actor);
      }

      @Override
      protected void processCurrentMessages(final ActorProcessingThread currentThread, final WebDebugger dbg) {
        assert actor instanceof ReplayActor;
        assert (size > 0);

        ReplayActor a = (ReplayActor) actor;

        boolean cont = true;
        Queue<EventualMessage> todo = new LinkedList<>();
        List<EventualMessage> rem = ((ReplayActor) actor).leftovers;

          if (a.replayCanProcess(firstMessage)) {
            todo.add(firstMessage);
            if (a.getReplayExpectedMessages().peek().createdPromises != null) {
              a.getReplayPromiseIds().addAll(a.getReplayExpectedMessages().peek().createdPromises);
            }
            a.getReplayExpectedMessages().remove();
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
              if (a.replayCanProcess(msg)) {
                todo.add(msg);
                if (a.getReplayExpectedMessages().peek().createdPromises != null) {
                  a.getReplayPromiseIds().addAll(a.getReplayExpectedMessages().peek().createdPromises);
                }
                a.getReplayExpectedMessages().remove();
                rem.remove(msg);
                cont = true;
                break;
              }
            }
          }

          baseMessageId = currentThread.generateMessageBaseId(todo.size());
          currentThread.currentMessageId = baseMessageId;

          for (EventualMessage msg : todo) {
            currentThread.currentMessage = msg;
            handleBreakPoints(firstMessage, dbg);
            msg.execute();
            currentThread.currentMessageId += 1;
          }

          currentThread.createdMessages += todo.size();
          ActorExecutionTrace.mailboxExecutedReplay(todo, baseMessageId, currentMailboxNo, actor);
      }
    }
  }
}
