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

import com.oracle.truffle.api.source.SourceSection;

import som.Output;
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

  /**
   * Types of elements in the trace, used to create a parse table.
   */
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

  private final HashMap<Short, SSymbol>             symbolMapping    = new HashMap<>();
  private ByteBuffer                                b                =
      ByteBuffer.allocate(ActorExecutionTrace.BUFFER_SIZE);
  private final HashMap<Long, ActorNode>            mappedActors     = new HashMap<>();
  private final HashMap<Long, Queue<MessageRecord>> expectedMessages = new HashMap<>();

  private long parsedMessages = 0;
  private long parsedActors   = 0;

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

    result[Implementation.IMPL_THREAD.getId()] = TraceRecord.IMPL_THREAD;
    result[Implementation.IMPL_CURRENT_ACTIVITY.getId()] =
        TraceRecord.IMPL_THREAD_CURRENT_ACTIVTITY;

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

  /**
   * Parse source section data see {@link TraceBuffer#writeSourceSection(SourceSection)}.
   */
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
    HashMap<Long, Context> messageReceiveContexts = new HashMap<>();

    HashMap<Long, Long> messagePromise = new HashMap<>();
    HashMap<Long, Long> messageSender = new HashMap<>();

    HashMap<Long, Context> threadToContext = new HashMap<>();

    long scopeId = -1;
    long currentThread = 0;
    Context current = new Context(0, 0, 0);

    Output.println("Parsing Trace ...");
    parseSymbols();

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
          case ACTIVITY_CREATION: {
            assert type == ActivityType.ACTOR.getCreationMarker() : "Only supporting actors at the moment";

            long newActivityId = b.getLong();
            b.getShort(); // symbol id

            if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
              readSourceSection(b);
            }

            if (newActivityId == 0) {
              assert !readMainActor : "There should be only one main actor.";
              readMainActor = true;
              if (!mappedActors.containsKey(newActivityId)) {
                mappedActors.put(newActivityId, new ActorNode(newActivityId));
              }
            } else {

              if (!mappedActors.containsKey(current.activityId)) {
                mappedActors.put(current.activityId, new ActorNode(current.activityId));
              }

              ActorNode node = mappedActors.containsKey(newActivityId)
                  ? mappedActors.get(newActivityId) : new ActorNode(newActivityId);
              node.mailboxNo = current.traceBufferId;
              mappedActors.get(current.activityId).addChild(node);
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

            if (!expectedMessages.containsKey(current.activityId)) {
              expectedMessages.put(current.activityId, new PriorityQueue<>());
            }

            if (!messageSender.containsKey(scopeId)) {
              // message not yet sent
              messageReceiveContexts.put(scopeId, current.clone()); // remember context
              current.msgNo++;
              break;
            }

            long sender = messageSender.remove(scopeId);

            if (messagePromise.containsKey(scopeId)) {
              // Promise Message
              long promise = messagePromise.get(scopeId);
              PromiseMessageRecord record = new PromiseMessageRecord(sender, promise,
                  current.traceBufferId, current.msgNo);
              records.add(record);
              expectedMessages.get(current.activityId).add(record);
            } else {
              // Regular Message
              expectedMessages.get(current.activityId).add(
                  new MessageRecord(sender, current.traceBufferId, current.msgNo));
            }
            current.msgNo++;
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
                chainedPromises.put(targetId, entityId);
              } else {
                promiseResolvers.put(targetId, current.activityId);
              }
            } else if (type == Marker.ACTOR_MSG_SEND) {
              if (messageReceiveContexts.containsKey(entityId)) {
                Context c = messageReceiveContexts.remove(entityId);
                expectedMessages.get(c.activityId).add(
                    new MessageRecord(current.activityId, c.traceBufferId, c.msgNo));
              } else {
                messageSender.put(entityId, current.activityId);
              }
            } else if (type == Marker.PROMISE_MSG_SEND) {
              if (messageReceiveContexts.containsKey(entityId)) {
                Context c = messageReceiveContexts.remove(entityId);
                PromiseMessageRecord record = new PromiseMessageRecord(current.activityId,
                    targetId, c.traceBufferId, c.msgNo);
                records.add(record);
                expectedMessages.get(c.activityId).add(record);
              } else {
                messageSender.put(entityId, current.activityId);
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
            currentThread = b.getLong(); // thread id
            if (threadToContext.containsKey(currentThread)) {
              // restore context, necessary after buffer swaps
              current = threadToContext.get(currentThread);
              current.traceBufferId++;
              current.msgNo = 0;
            }
            assert b.position() == Implementation.IMPL_THREAD.getSize() - 1;
            break;
          case IMPL_THREAD_CURRENT_ACTIVTITY: {
            long activityId = b.getLong();
            int traceBufferId = b.getInt();
            current = new Context(activityId, traceBufferId, 0);
            threadToContext.put(currentThread, current);

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
          default:
            assert false;
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
        /*
         * Promise resolutions done by the TimerPrim aren't included in the trace file,
         * pretend they were resolved by the main actor
         */
        pmr.pId = 0;
      } else {
        pmr.pId = promiseResolvers.get(pid);
      }
    }

    assert messageReceiveContexts.isEmpty();

    Output.println("Trace with " + parsedMessages + " Messages and " + parsedActors
        + " Actors sucessfully parsed!");
  }

  /**
   * Information about current executing activity on a implementation thread.
   */
  private static class Context {
    final long activityId;
    int        traceBufferId;
    int        msgNo;

    Context(final long activityId, final int tracebufferId, final int msgNo) {
      super();
      this.activityId = activityId;
      this.traceBufferId = tracebufferId;
      this.msgNo = msgNo;
    }

    @Override
    protected Context clone() {
      return new Context(activityId, traceBufferId, msgNo);
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
