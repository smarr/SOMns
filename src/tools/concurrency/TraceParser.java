package tools.concurrency;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Queue;

import som.VM;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vmobjects.SSymbol;
import tools.debugger.entities.ActivityType;
import tools.debugger.entities.DynamicScopeType;
import tools.debugger.entities.Implementation;
import tools.debugger.entities.Marker;
import tools.debugger.entities.PassiveEntityType;
import tools.debugger.entities.ReceiveOp;
import tools.debugger.entities.SendOp;


public final class TraceParser {

  private enum TraceRecord {
    ACTIVITY_CREATION,
    ACTIVITY_COMPLETION,
    DYNAMIC_SCOPE_START,
    DYNAMIC_SCOPE_END,
    PASSIVE_ENTITY_CREATION,
    SEND_OP,
    RECEIVE_OP,
    IMPL_THREAD,
    IMPL_THREAD_CURRENT_ACTIVTITY
  }


  private final HashMap<Short, SSymbol> symbolMapping = new HashMap<>();
  private ByteBuffer b = ByteBuffer.allocate(ActorExecutionTrace.BUFFER_SIZE);
  private final HashMap<Long, ActorNode> mappedActors = new HashMap<>();
  private final HashMap<Long, Queue<MessageRecord>> expectedMessages = new HashMap<>();

  private long scopeId = -1;
  private long activityId = -1;
  private int traceBufferId = -1;

  private long parsedMessages = 0;
  private long parsedActors = 0;

  private static TraceParser parser;

  private final TraceRecord[] parseTable;

  public static synchronized Queue<MessageRecord> getExpectedMessages(final long replayId) {
    if (parser == null) {
      parser = new TraceParser();
      parser.parseTrace();
    }
    return parser.expectedMessages.remove(replayId);
  }

  public static synchronized long getReplayId(final long parentId, final int childNo) {
    if (parser == null) {
      parser = new TraceParser();
      parser.parseTrace();
    }
    assert parser.mappedActors.containsKey(parentId) : "Parent doesn't exist";
    return parser.mappedActors.get(parentId).getChild(childNo).actorId;
  }

  private TraceParser() {
    assert VmSettings.REPLAY;
    this.parseTable = createParseTable();
  }

  private TraceRecord[] createParseTable() {
    TraceRecord[] result = new TraceRecord[Marker.PROMISE_MSG_SEND + 1];

    for (ActivityType t : ActivityType.values()) {
      if (t.getCreationMarker() != 0) {
        result[t.getCreationMarker()] = TraceRecord.ACTIVITY_CREATION;
      }
      if (t.getCompletionMarker() != 0) {
        result[t.getCompletionMarker()] = TraceRecord.ACTIVITY_COMPLETION;
      }
    }

    for (DynamicScopeType t : DynamicScopeType.values()) {
      if (t.getStartMarker() != 0) {
        result[t.getStartMarker()] = TraceRecord.DYNAMIC_SCOPE_START;
      }
      if (t.getEndMarker() != 0) {
        result[t.getEndMarker()] = TraceRecord.DYNAMIC_SCOPE_END;
      }
    }

    for (PassiveEntityType t : PassiveEntityType.values()) {
      if (t.getCreationMarker() != 0) {
        result[t.getCreationMarker()] = TraceRecord.PASSIVE_ENTITY_CREATION;
      }
    }

    for (SendOp t : SendOp.values()) {
      result[t.getId()] = TraceRecord.SEND_OP;
    }

    for (ReceiveOp t : ReceiveOp.values()) {
      result[t.getId()] = TraceRecord.RECEIVE_OP;
    }

    result[Implementation.IMPL_THREAD.getId()]           = TraceRecord.IMPL_THREAD;
    result[Implementation.IMPL_CURRENT_ACTIVITY.getId()] = TraceRecord.IMPL_THREAD_CURRENT_ACTIVTITY;

    return result;
  }

  private void parseSymbols() {
    File symbolFile = new File(VmSettings.TRACE_FILE + ".sym");
    // create mapping from old to new symbol ids
    try (FileInputStream fis = new FileInputStream(symbolFile);
        BufferedReader br = new BufferedReader(new InputStreamReader(fis))) {
      br.lines().forEach((l) -> {
        String[] a = l.split(":", 2);
        symbolMapping.put(Short.parseShort(a[0]), Symbols.symbolFor(a[1]));
      });
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void readSourceSection(final ByteBuffer b) {
    b.getShort();
    b.getShort();
    b.getShort();
    b.getShort();
  }

  private void parseTrace() {
    boolean readMainActor = false;
    File traceFile = new File(VmSettings.TRACE_FILE + ".trace");

    HashMap<Long, Long> chainedPromises = new HashMap<>();
    HashMap<Long, Long> promiseResolvers = new HashMap<>();
    ArrayList<PromiseMessageRecord> records = new ArrayList<>();
    HashMap<Long, UnsentMessage> unsentMessages = new HashMap<>();

    HashMap<Long, Long> messagePromise = new HashMap<>();
    HashMap<Long, Long> messageSender = new HashMap<>();
    int msgNo = 0;

    VM.println("Parsing Trace ...");
    parseSymbols();

    try (FileInputStream fis = new FileInputStream(traceFile); FileChannel channel = fis.getChannel()) {
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
          case ACTIVITY_CREATION: {
            assert type == ActivityType.ACTOR.getCreationMarker() : "Only supporting actors at the moment";

            long newActivityId = b.getLong();
            short symbolId  = b.getShort();

            if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
              readSourceSection(b);
            }

            if (newActivityId == 0) {
              assert !readMainActor : "There should be only one main actor.";
              readMainActor = true;
            }

            if (!mappedActors.containsKey(activityId)) {
              mappedActors.put(activityId, new ActorNode(activityId));
            }

            if (!mappedActors.containsKey(newActivityId)) {
              ActorNode node = new ActorNode(newActivityId);
              mappedActors.put(newActivityId, new ActorNode(newActivityId));
              node.mailboxNo = traceBufferId;
              mappedActors.get(activityId).addChild(node);
            }
            parsedActors++;

            assert b.position() == start + ActivityType.ACTOR.getCreationSize();
            break;
          }
          case ACTIVITY_COMPLETION:
            assert b.position() == start + ActivityType.ACTOR.getCompletionSize();
            break;
          case DYNAMIC_SCOPE_START:
            scopeId = b.getLong();
            if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
              readSourceSection(b);
            }

            // VM.println("R" + scopeId);

            if (!expectedMessages.containsKey(activityId)) {
              expectedMessages.put(activityId, new PriorityQueue<>());
            }

            if (!messageSender.containsKey(scopeId)) {
              // message not yet sent

              unsentMessages.put(scopeId, new UnsentMessage(activityId, msgNo, traceBufferId)); // add receiver
              msgNo++;
              break;
            }

            long sender = messageSender.remove(scopeId);

            if (messagePromise.containsKey(scopeId)) {
              // Promise Message
              long promise = messagePromise.get(scopeId);
              PromiseMessageRecord record = new PromiseMessageRecord(sender, promise, traceBufferId, msgNo);
              records.add(record);
              expectedMessages.get(activityId).add(record);
            } else {
              // Regular Message

              expectedMessages.get(activityId).add(new MessageRecord(sender, traceBufferId, msgNo));
            }
            msgNo++;
            assert b.position() == start + DynamicScopeType.TRANSACTION.getStartSize();
            break;
          case DYNAMIC_SCOPE_END:
            scopeId = -1;
            assert b.position() == start + DynamicScopeType.TRANSACTION.getEndSize();
            break;
          case SEND_OP:
            long entityId = b.getLong();
            long targetId = b.getLong();


            if (type == Marker.PROMISE_RESOLUTION) {
              if (entityId != 0) {
                // VM.println("" + entityId + " chained " + targetId);
                chainedPromises.put(targetId, entityId);
              } else {
                // VM.println("" + targetId + " resolved by " + activityId);
                promiseResolvers.put(targetId, activityId);
              }
            } else if (type == Marker.ACTOR_MSG_SEND) {
              // VM.println("S" + entityId + "   " + (type == Marker.PROMISE_MSG_SEND));
              if (unsentMessages.containsKey(entityId)) {
                UnsentMessage um = unsentMessages.remove(entityId);
                expectedMessages.get(um.activityId).add(new MessageRecord(activityId, um.tracebufferId, um.msgNo));
              } else {
                messageSender.put(entityId, activityId);
              }
            } else if (type == Marker.PROMISE_MSG_SEND) {
              // VM.println("S" + entityId + "   " + (type == Marker.PROMISE_MSG_SEND));
              if (unsentMessages.containsKey(entityId)) {
                UnsentMessage um = unsentMessages.remove(entityId);
                PromiseMessageRecord record = new PromiseMessageRecord(activityId, targetId, um.tracebufferId, um.msgNo);
                records.add(record);
                expectedMessages.get(um.activityId).add(record);
              } else {
                messageSender.put(entityId, activityId);
                messagePromise.put(entityId, targetId);
              }

            }
            parsedMessages++;
            assert b.position() == start + SendOp.ACTOR_MSG.getSize();
            break;
          case RECEIVE_OP:
            b.getLong();
            assert b.position() == start + ReceiveOp.CHANNEL_RCV.getSize();
            break;
          case IMPL_THREAD:
            b.compact();
            channel.read(b);
            b.flip();
            b.getLong(); // thread id
            assert b.position() == Implementation.IMPL_THREAD.getSize() - 1;
            break;
          case IMPL_THREAD_CURRENT_ACTIVTITY: {
            activityId = b.getLong();
            traceBufferId = b.getInt();
            msgNo = 0;
            assert b.position() == start + Implementation.IMPL_CURRENT_ACTIVITY.getSize();
            break;
          }
          case PASSIVE_ENTITY_CREATION: {
           b.getLong();
           if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
             readSourceSection(b);
           }
           assert b.position() == start + PassiveEntityType.PROMISE.getCreationSize();
           break;
          }
          default: assert false;
        }
      }

    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    for (PromiseMessageRecord pmr : records) {
      long pid = pmr.pId;
      while (chainedPromises.containsKey(pid)) {
        pid = chainedPromises.get(pid);
      }

      if (!promiseResolvers.containsKey(pid)) {
        /* Promise resolutions done by the TimerPrim aren't included in the trace file,
           pretend they were resolved by the main actor */
        pmr.pId = 0;
      } else {
        pmr.pId = promiseResolvers.get(pid);
      }
    }

    /*
    VM.println("unhandled" + messageSender.size());
    for (long l : messageSender.keySet()) {
      VM.println("" + l);
    }*/

    assert unsentMessages.isEmpty();

    VM.println("Trace with " + parsedMessages + " Messages and " + parsedActors + " Actors sucessfully parsed!");
  }

  private static class UnsentMessage {
    int msgNo;
    int tracebufferId;
    long activityId;

    public UnsentMessage(final long activityId, final int msgNo, final int tracebufferId) {
      super();
      this.activityId = activityId;
      this.msgNo = msgNo;
      this.tracebufferId = tracebufferId;
    }
  }

  private static class ActorNode implements Comparable<ActorNode> {
    final long actorId;
    int childNo;
    int mailboxNo;
    boolean sorted = false;
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
      if (!sorted) {
        Collections.sort(children);
        sorted = true;
      }

      assert children != null : "Actor does not exist in trace!";
      assert children.size() > childNo : "Actor does not exist in trace!";
      return children.get(childNo);
    }

    @Override
    public String toString() {
      return "" + actorId + ":" + childNo;
    }
  }

  public static class MessageRecord implements Comparable<MessageRecord> {
    public final long sender;
    public final int mailboxNo;
    public final int messageNo;

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

  public static class PromiseMessageRecord extends MessageRecord{
    public long pId;

    public PromiseMessageRecord(final long sender, final long pId, final int mb, final int no) {
      super(sender, mb, no);
      this.pId = pId;
    }
  }
}
