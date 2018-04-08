package tools.concurrency;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Queue;

import som.Output;
import som.vm.VmSettings;
import som.vmobjects.SSymbol;
import tools.debugger.entities.ActivityType;
import tools.debugger.entities.DynamicScopeType;
import tools.debugger.entities.ReceiveOp;


public final class TraceParser {

  /**
   * Types of elements in the trace, used to create a parse table.
   */
  private enum TraceRecord {
    ACTOR_CREATION,
    ACTOR_CONTEXT,
    MESSAGE,
    PROMISE_MESSAGE,
    SYSTEM_CALL
  }

  private final HashMap<Short, SSymbol>                    symbolMapping    = new HashMap<>();
  private ByteBuffer                                       b                =
      ByteBuffer.allocate(TracingBackend.BUFFER_SIZE);
  private final HashMap<Integer, ActorNode>                mappedActors     = new HashMap<>();
  private final HashMap<Integer, ArrayList<MessageRecord>> expectedMessages = new HashMap<>();

  private long parsedMessages = 0;
  private long parsedActors   = 0;

  private static TraceParser parser;

  private final TraceRecord[] parseTable;

  public static synchronized Queue<MessageRecord> getExpectedMessages(final int replayId) {
    if (parser == null) {
      parser = new TraceParser();
      parser.parseTrace();
    }
    return null;// parser.expectedMessages.remove(replayId);
  }

  public static synchronized int getReplayId(final int parentId, final int childNo) {
    if (parser == null) {
      parser = new TraceParser();
      parser.parseTrace();
    }
    assert parser.mappedActors.containsKey(parentId) : "Parent doesn't exist";
    return (int) parser.mappedActors.get(parentId).getChild(childNo).actorId;
  }

  private TraceParser() {
    assert VmSettings.REPLAY;
    this.parseTable = createParseTable();
  }

  private TraceRecord[] createParseTable() {
    TraceRecord[] result = new TraceRecord[8];

    result[ActorExecutionTrace.ACTOR_CREATION] = TraceRecord.ACTOR_CREATION;
    result[ActorExecutionTrace.ACTOR_CONTEXT] = TraceRecord.ACTOR_CONTEXT;
    result[ActorExecutionTrace.MESSAGE] = TraceRecord.MESSAGE;
    result[ActorExecutionTrace.PROMISE_MESSAGE] = TraceRecord.PROMISE_MESSAGE;
    result[ActorExecutionTrace.SYSTEM_CALL] = TraceRecord.SYSTEM_CALL;

    return result;
  }

  private void parseTrace() {
    boolean readMainActor = false;
    File traceFile = new File(VmSettings.TRACE_FILE + ".trace");

    int sender, resolver;
    int currentActor = 0;
    int currentOrdering = 0;

    Output.println("Parsing Trace ...");

    try (FileInputStream fis = new FileInputStream(traceFile);
        FileChannel channel = fis.getChannel()) {
      channel.read(b);
      b.flip(); // prepare for reading from buffer
      while (channel.position() < channel.size() || b.remaining() > 0) {
        // read from file if buffer is empty
        if (!b.hasRemaining()) {
          b.clear();
          channel.read(b);
          b.flip();
        }

        final int start = b.position();
        final byte type = b.get();
        TraceRecord recordType = parseTable[type];

        switch (recordType) {
          case ACTOR_CREATION: {

            int newActorId = b.getInt();

            if (newActorId == 0) {
              assert !readMainActor : "There should be only one main actor.";
              readMainActor = true;
              if (!mappedActors.containsKey(newActorId)) {
                mappedActors.put(newActorId, new ActorNode(newActorId));
              }
            } else {
              if (!mappedActors.containsKey(currentActor)) {
                mappedActors.put(currentActor, new ActorNode(currentActor));
              }

              ActorNode node = mappedActors.containsKey(newActorId)
                  ? mappedActors.get(newActorId)
                  : new ActorNode(newActorId);
              node.mailboxNo = currentOrdering;
              mappedActors.get(currentActor).addChild(node);
            }
            parsedActors++;

            assert b.position() == start + ActivityType.ACTOR.getCreationSize();
            break;
          }
          case ACTOR_CONTEXT:
            currentOrdering = Byte.toUnsignedInt(b.get());
            currentActor = b.getInt();
            assert b.position() == start + DynamicScopeType.TRANSACTION.getStartSize();
            break;
          case MESSAGE:
            sender = b.getInt();
            assert b.position() == start + ReceiveOp.CHANNEL_RCV.getSize();
            break;
          case PROMISE_MESSAGE:
            sender = b.getInt();
            resolver = b.getInt();
          case SYSTEM_CALL:
          default:
            assert false;
        }
      }

    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    Output.println("Trace with " + parsedMessages + " Messages and " + parsedActors
        + " Actors sucessfully parsed!");
  }

  /**
   * Information about current executing activity on a implementation thread.
   */
  private static class Context {
    final int actorId;
    byte      traceBufferId;

    Context(final int activityId, final byte tracebufferId) {
      super();
      this.actorId = activityId;
      this.traceBufferId = tracebufferId;
    }

    @Override
    protected Context clone() {
      return new Context(actorId, traceBufferId);
    }
  }

  /**
   * Node in actor creation hierarchy.
   */
  private static class ActorNode implements Comparable<ActorNode> {
    final long           actorId;
    int                  childNo;
    int                  mailboxNo;
    boolean              sorted = false;
    ArrayList<ActorNode> children;

    ActorNode(final long actorId) {
      super();
      this.actorId = actorId;
    }

    @Override
    public int compareTo(final ActorNode o) {
      int i = Integer.compare(mailboxNo, o.mailboxNo);
      if (i == 0) {
        i = Integer.compare(childNo, o.childNo);
      }
      if (i == 0) {
        i = Long.compare(actorId, o.actorId);
      }

      return i;
    }

    private void addChild(final ActorNode child) {
      if (children == null) {
        children = new ArrayList<>();
      }

      child.childNo = children.size();
      children.add(child);
    }

    protected ActorNode getChild(final int childNo) {
      assert children != null : "Actor does not exist in trace!";
      assert children.size() > childNo : "Actor does not exist in trace!";

      if (!sorted) {
        Collections.sort(children);
        sorted = true;
      }

      return children.get(childNo);
    }

    @Override
    public String toString() {
      return "" + actorId + ":" + childNo;
    }
  }

  public static class MessageRecord implements Comparable<MessageRecord> {
    public final long sender;
    public final int  mailboxNo;
    public final int  messageNo;

    public MessageRecord(final long sender, final int mb, final int no) {
      super();
      this.sender = sender;
      this.mailboxNo = mb;
      this.messageNo = no;
    }

    @Override
    public int compareTo(final MessageRecord o) {
      if (mailboxNo == o.mailboxNo) {
        return messageNo - o.messageNo;
      }

      return mailboxNo - o.mailboxNo;
    }
  }

  public static class PromiseMessageRecord extends MessageRecord {
    public long pId;

    public PromiseMessageRecord(final long sender, final long pId, final int mb,
        final int no) {
      super(sender, mb, no);
      this.pId = pId;
    }
  }
}
