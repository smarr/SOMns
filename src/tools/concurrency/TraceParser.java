package tools.concurrency;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Queue;

import som.Output;
import som.interpreter.actors.EventualMessage;
import som.vm.VmSettings;
import tools.concurrency.TracingActors.ReplayActor;
import tools.replay.actors.ActorExecutionTrace;
import tools.replay.nodes.RecordEventNodes;


public final class TraceParser {

  /**
   * Types of elements in the trace, used to create a parse table.
   */
  private enum TraceRecord {
    ACTOR_CREATION,
    ACTOR_CONTEXT,
    MESSAGE,
    PROMISE_MESSAGE,
    SYSTEM_CALL,
    CHANNEL_CREATION,
    PROCESS_CONTEXT,
    CHANNEL_READ,
    CHANNEL_WRITE,
    PROCESS_CREATION
  }

  private ByteBuffer                      b        =
      ByteBuffer.allocate(VmSettings.BUFFER_SIZE);
  private final HashMap<Long, EntityNode> entities = new HashMap<>();

  private long parsedMessages = 0;
  private long parsedEntities = 0;

  private static TraceParser parser;
  private static String      traceName =
      VmSettings.TRACE_FILE + (VmSettings.SNAPSHOTS_ENABLED ? ".0" : "");

  private final TraceRecord[] parseTable;

  private boolean readMainActor = false;

  public static ByteBuffer getExternalData(final long actorId, final int dataId) {
    long pos = parser.entities.get(actorId).externalData.get(dataId);
    return parser.readExternalData(pos);
  }

  public static int getIntegerSysCallResult() {
    ReplayActor ra = (ReplayActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    ByteBuffer bb = getExternalData(ra.getId(), ra.getDataId());
    return bb.getInt();
  }

  public static long getLongSysCallResult() {
    ReplayActor ra = (ReplayActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    ByteBuffer bb = getExternalData(ra.getId(), ra.getDataId());
    return bb.getLong();
  }

  public static double getDoubleSysCallResult() {
    ReplayActor ra = (ReplayActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    ByteBuffer bb = getExternalData(ra.getId(), ra.getDataId());
    return bb.getDouble();
  }

  public static String getStringSysCallResult() {
    ReplayActor ra = (ReplayActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    ByteBuffer bb = getExternalData(ra.getId(), ra.getDataId());
    return new String(bb.array());
  }

  public static synchronized Queue<MessageRecord> getExpectedMessages(final long replayId) {
    if (parser == null) {
      parser = new TraceParser();
      parser.parseTrace();
    }

    assert parser.entities.containsKey(replayId) : "Missing expected Messages for Actor: "
        + replayId;
    assert parser.entities.get(replayId) instanceof ActorNode;
    return ((ActorNode) parser.entities.get(replayId)).getExpectedMessages();
  }

  public static synchronized long getReplayId(final long parentId, final int childNo) {
    if (parser == null) {
      parser = new TraceParser();
      parser.parseTrace();
    }

    assert parser.entities.containsKey(parentId) : "Parent doesn't exist";
    return parser.entities.get(parentId).getChild(childNo).entityId;
  }

  private TraceParser() {
    assert VmSettings.REPLAY;
    this.parseTable = createParseTable();
    b.order(ByteOrder.LITTLE_ENDIAN);
  }

  private TraceRecord[] createParseTable() {
    TraceRecord[] result = new TraceRecord[16];

    result[ActorExecutionTrace.ACTOR_CREATION] = TraceRecord.ACTOR_CREATION;
    result[ActorExecutionTrace.ACTOR_CONTEXT] = TraceRecord.ACTOR_CONTEXT;
    result[ActorExecutionTrace.MESSAGE] = TraceRecord.MESSAGE;
    result[ActorExecutionTrace.PROMISE_MESSAGE] = TraceRecord.PROMISE_MESSAGE;
    result[ActorExecutionTrace.SYSTEM_CALL] = TraceRecord.SYSTEM_CALL;
    result[ActorExecutionTrace.CHANNEL_CREATE] = TraceRecord.CHANNEL_CREATION;
    result[ActorExecutionTrace.PROCESS_CONTEXT] = TraceRecord.PROCESS_CONTEXT;
    result[ActorExecutionTrace.CHANNEL_READ] = TraceRecord.CHANNEL_READ;
    result[ActorExecutionTrace.CHANNEL_WRITE] = TraceRecord.CHANNEL_WRITE;
    result[ActorExecutionTrace.PROCESS_CREATE] = TraceRecord.PROCESS_CREATION;

    return result;
  }

  private void parseTrace() {

    File traceFile = new File(traceName + ".trace");

    long startTime = System.currentTimeMillis();

    Output.println("Parsing Trace ...");

    try (FileInputStream fis = new FileInputStream(traceFile);
        FileChannel channel = fis.getChannel()) {

      // event context
      EntityNode currentEntity = null;
      long currentEntityId = 0;
      int ordering = 0;

      channel.read(b);
      b.flip(); // prepare for reading from buffer

      while (channel.position() < channel.size() || b.remaining() > 0) {

        long sender = 0;
        long resolver = 0;
        long edat = 0;
        short method = 0;
        int dataId = 0;

        // read from file if buffer is empty
        if (!b.hasRemaining()) {
          b.clear();
          channel.read(b);
          b.flip();
        } else if (b.remaining() < 20) {
          b.compact();
          channel.read(b);
          b.flip();
        }

        final int start = b.position();
        final byte type = b.get();
        final int numbytes = Long.BYTES;
        boolean external = (type & ActorExecutionTrace.EXTERNAL_BIT) != 0;
        TraceRecord recordType = parseTable[type & (ActorExecutionTrace.EXTERNAL_BIT - 1)];

        switch (recordType) {
          case ACTOR_CREATION:
          case CHANNEL_CREATION:
          case PROCESS_CREATION:
            long newEntityId = getId(numbytes);

            if (newEntityId == 0) {
              assert !readMainActor : "There should be only one main actor.";
              readMainActor = true;
            }

            EntityNode newEntity = getOrCreateEntityEntry(recordType, newEntityId);
            newEntity.ordering = ordering;
            currentEntity.addChild(newEntity);
            parsedEntities++;
            assert b.position() == start + RecordEventNodes.ONE_EVENT_SIZE;
            break;

          case ACTOR_CONTEXT:
          case PROCESS_CONTEXT:
            ordering = Short.toUnsignedInt(b.getShort());
            currentEntityId = getId(Long.BYTES);
            currentEntity = getOrCreateEntityEntry(recordType, currentEntityId);
            assert currentEntity != null;
            currentEntity.onContextStart(ordering);
            assert b.position() == start + 11;
            break;

          case MESSAGE:
            parsedMessages++;
            sender = getId(numbytes);

            if (external) {
              edat = b.getLong();
              method = (short) (edat >> 32);
              dataId = (int) edat;
              ((ActorNode) currentEntity).addMessageRecord(
                  new ExternalMessageRecord(sender, method, dataId));
              assert b.position() == start + RecordEventNodes.TWO_EVENT_SIZE;
            } else {
              ((ActorNode) currentEntity).addMessageRecord(new MessageRecord(sender));
              assert b.position() == start + RecordEventNodes.ONE_EVENT_SIZE;
            }
            break;
          case PROMISE_MESSAGE:
            parsedMessages++;
            sender = getId(numbytes);
            resolver = getId(numbytes);

            if (external) {
              edat = b.getLong();
              method = (short) (edat >> 32);
              dataId = (int) edat;
              ((ActorNode) currentEntity).addMessageRecord(
                  new ExternalPromiseMessageRecord(sender, resolver, method, dataId));
              assert b.position() == start + RecordEventNodes.THREE_EVENT_SIZE;
            } else {
              ((ActorNode) currentEntity).addMessageRecord(
                  new PromiseMessageRecord(sender, resolver));
              assert b.position() == start + RecordEventNodes.TWO_EVENT_SIZE;
            }

            break;
          case SYSTEM_CALL:
            dataId = b.getInt();
            break;
          case CHANNEL_READ:
            long channelRId = b.getLong();
            long nread = b.getLong();
            // give the specified channel a priorityqueue
            // add the current activity Id with priority nreads
            // The priority queue then contains the order in which activities read
            assert b.position() == start + RecordEventNodes.TWO_EVENT_SIZE;
            break;
          case CHANNEL_WRITE:
            long channelWId = b.getLong();
            long nwrite = b.getLong();
            // give the specified channel a priorityqueue
            // add the current activity Id with priority nwrites
            // The priority queue then contains the order in which activities write
            assert b.position() == start + RecordEventNodes.TWO_EVENT_SIZE;
            break;
          default:
            assert false;
        }
      }

    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (AssertionError e) {
      e.printStackTrace();
    } catch (Throwable e) {
      e.printStackTrace();
    }

    parseExternalData();

    long end = System.currentTimeMillis();
    Output.println("Trace with " + parsedMessages + " Messages and " + parsedEntities
        + " Actors sucessfully parsed in " + (end - startTime) + "ms !");
  }

  private EntityNode getOrCreateEntityEntry(final TraceRecord type, final long entityId) {
    if (entities.containsKey(entityId)) {
      return entities.get(entityId);
    } else {
      EntityNode newNode = null;
      switch (type) {
        case ACTOR_CONTEXT:
        case ACTOR_CREATION:
          newNode = new ActorNode(entityId);
          break;
        case CHANNEL_CREATION:
          // TODO
          break;
        case PROCESS_CONTEXT:
        case PROCESS_CREATION:
          // TODO
          break;
        default:
          throw new IllegalArgumentException("Should never happen" + type);
      }

      entities.put(entityId, newNode);
      return newNode;
    }
  }

  private ByteBuffer readExternalData(final long position) {
    File traceFile = new File(traceName + ".dat");
    try (FileInputStream fis = new FileInputStream(traceFile);
        FileChannel channel = fis.getChannel()) {

      ByteBuffer bb = ByteBuffer.allocate(16);
      bb.order(ByteOrder.LITTLE_ENDIAN);

      channel.read(bb, position);
      bb.flip();

      bb.getLong(); // actorId
      bb.getInt(); // dataId
      int len = bb.getInt();

      ByteBuffer res = ByteBuffer.allocate(len);
      res.order(ByteOrder.LITTLE_ENDIAN);
      channel.read(res, position + 16);
      res.flip();
      return res;
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void parseExternalData() {
    File traceFile = new File(traceName + ".dat");

    ByteBuffer bb = ByteBuffer.allocate(16);
    bb.order(ByteOrder.LITTLE_ENDIAN);

    try (FileInputStream fis = new FileInputStream(traceFile);
        FileChannel channel = fis.getChannel()) {
      while (channel.position() < channel.size()) {
        // read from file if buffer is empty

        long position = channel.position();
        bb.clear();
        channel.read(bb);
        bb.flip();

        long actor = bb.getLong();
        int dataId = bb.getInt();
        int len = bb.getInt();

        EntityNode en = entities.get(actor);
        assert en != null;
        if (en.externalData == null) {
          en.externalData = new HashMap<>();
        }

        en.externalData.put(dataId, position);

        position = channel.position();
        channel.position(position + len);
      }

    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private long getId(final int numbytes) {
    switch (numbytes) {
      case 1:
        return 0 | b.get();
      case Short.BYTES:
        return 0 | b.getShort();
      case 3:
        return (b.get() << 16) | b.getShort();
      case Integer.BYTES:
        return b.getInt();
      case Long.BYTES:
        return b.getLong();
    }
    assert false : "should not happen";
    return 0;
  }

  private static abstract class EntityNode implements Comparable<EntityNode> {
    final long             entityId;
    int                    childNo;
    HashMap<Integer, Long> externalData;
    int                    ordering;
    ArrayList<EntityNode>  children;
    boolean                childrenSorted = false;

    public EntityNode(final long entityId) {
      this.entityId = entityId;
    }

    void addChild(final EntityNode child) {
      if (children == null) {
        children = new ArrayList<>();
      }

      child.childNo = children.size();
      children.add(child);
    }

    protected EntityNode getChild(final int childNo) {
      assert children != null : "Actor does not exist in trace!";
      assert children.size() > childNo : "Actor does not exist in trace!";

      if (!childrenSorted) {
        Collections.sort(children);
        childrenSorted = true;
      }

      return children.get(childNo);
    }

    @Override
    public int compareTo(final EntityNode o) {
      int i = Integer.compare(ordering, o.ordering);
      if (i == 0) {
        i = Integer.compare(childNo, o.childNo);
      }
      if (i == 0) {
        i = Long.compare(entityId, o.entityId);
      }

      return i;
    }

    public void onContextStart(final int ordering) {}

    @Override
    public String toString() {
      return "" + entityId + ":" + childNo;
    }
  }

  /**
   * Node in actor creation hierarchy.
   */
  private static class ActorNode extends EntityNode {

    HashMap<Integer, ArrayList<MessageRecord>> bucket1          = new HashMap<>();
    HashMap<Integer, ArrayList<MessageRecord>> bucket2          = new HashMap<>();
    Queue<MessageRecord>                       expectedMessages = new java.util.LinkedList<>();
    int                                        max              = 0;
    int                                        max2             = 0;
    ArrayList<MessageRecord>                   mailbox          = null;

    int currentOrdering;

    ActorNode(final long actorId) {
      super(actorId);
    }

    /*
     * make two buckets, one for the current 65k contexs, and one for those that we
     * encounter prematurely
     * decision where stuff goes is made based on bitset or whether the ordering byte
     * already is used in the first bucket
     * when a bucket is full, we sort the contexts and put the messages inside a queue,
     * context can then be reclaimed by GC
     */
    private void newMailbox() {
      mailbox = new ArrayList<>();

      if (bucket1.containsKey(currentOrdering)) {
        // use bucket two
        assert !bucket2.containsKey(currentOrdering);
        bucket2.put(currentOrdering, mailbox);
        max2 = Math.max(max2, currentOrdering);
        assert max2 < 0x8FFF;
      } else {
        assert !bucket2.containsKey(currentOrdering);
        bucket1.put(currentOrdering, mailbox);
        max = Math.max(max, currentOrdering);
        if (max == 0xFFFF) {
          // Bucket 1 is full, switch
          assert max2 < 0x8FFF;
          for (int i = 0; i <= max; i++) {
            if (!bucket1.containsKey(i)) {
              continue;
            }
            expectedMessages.addAll(bucket1.get(i));
          }
          bucket1.clear();
          HashMap<Integer, ArrayList<MessageRecord>> temp = bucket1;
          bucket1 = bucket2;
          bucket2 = temp;
          max = max2;
          max2 = 0;
        }
      }
    }

    private void addMessageRecord(final MessageRecord mr) {
      if (mailbox == null) {
        newMailbox();
      }
      mailbox.add(mr);
    }

    @Override
    public void onContextStart(final int ordering) {
      currentOrdering = ordering;
      mailbox = null;
    }

    public Queue<MessageRecord> getExpectedMessages() {

      assert bucket1.size() < 0xFFFF;
      assert bucket2.isEmpty();
      for (int i = 0; i <= max; i++) {
        if (bucket1.containsKey(i)) {
          expectedMessages.addAll(bucket1.get(i));
        } else {
          continue;
        }
      }

      for (int i = 0; i <= max2; i++) {
        if (bucket2.containsKey(i)) {
          expectedMessages.addAll(bucket2.get(i));
        } else {
          continue;
        }
      }
      return expectedMessages;
    }
  }

  public static class MessageRecord {
    public final long sender;

    public MessageRecord(final long sender) {
      super();
      this.sender = sender;
    }

    public boolean isExternal() {
      return false;
    }
  }

  public static class ExternalMessageRecord extends MessageRecord {
    public final short method;
    public final int   dataId;

    public ExternalMessageRecord(final long sender, final short method, final int dataId) {
      super(sender);
      this.method = method;
      this.dataId = dataId;
    }

    @Override
    public boolean isExternal() {
      return true;
    }
  }

  public static class PromiseMessageRecord extends MessageRecord {
    public long pId;

    public PromiseMessageRecord(final long sender, final long resolver) {
      super(sender);
      this.pId = resolver;
    }
  }

  public static class ExternalPromiseMessageRecord extends PromiseMessageRecord {
    public final int   dataId;
    public final short method;

    public ExternalPromiseMessageRecord(final long sender, final long resolver,
        final short method,
        final int extData) {
      super(sender, resolver);
      this.method = method;
      this.dataId = extData;
    }

    @Override
    public boolean isExternal() {
      return true;
    }
  }

}
