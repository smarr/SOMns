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

  private ByteBuffer                        b                =
      ByteBuffer.allocate(VmSettings.BUFFER_SIZE);
  private final HashMap<Integer, ActorNode> actors           = new HashMap<>();
  private final HashMap<Long, Long>         externalDataDict = new HashMap<>();

  private long parsedMessages = 0;
  private long parsedActors   = 0;

  private static TraceParser parser;
  private static String      traceName =
      VmSettings.TRACE_FILE + (VmSettings.SNAPSHOTS_ENABLED ? ".0" : "");

  private final TraceRecord[] parseTable;

  public static ByteBuffer getExternalData(final int actorId, final int dataId) {
    long key = (((long) actorId) << 32) | dataId;
    long pos = parser.externalDataDict.get(key);
    return parser.readExternalData(pos);
  }

  public static int getIntegerSysCallResult() {
    ReplayActor ra = (ReplayActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    ByteBuffer bb = getExternalData(ra.getActorId(), ra.getDataId());
    return bb.getInt();
  }

  public static long getLongSysCallResult() {
    ReplayActor ra = (ReplayActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    ByteBuffer bb = getExternalData(ra.getActorId(), ra.getDataId());
    return bb.getLong();
  }

  public static double getDoubleSysCallResult() {
    ReplayActor ra = (ReplayActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    ByteBuffer bb = getExternalData(ra.getActorId(), ra.getDataId());
    return bb.getDouble();
  }

  public static String getStringSysCallResult() {
    ReplayActor ra = (ReplayActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    ByteBuffer bb = getExternalData(ra.getActorId(), ra.getDataId());
    return new String(bb.array());
  }

  public static synchronized Queue<MessageRecord> getExpectedMessages(final int replayId) {
    if (parser == null) {
      parser = new TraceParser();
      parser.parseTrace();
    }

    return parser.actors.get(replayId).getExpectedMessages();
  }

  public static synchronized int getReplayId(final int parentId, final int childNo) {
    if (parser == null) {
      parser = new TraceParser();
      parser.parseTrace();
    }
    assert parser.actors.containsKey(parentId) : "Parent doesn't exist";
    return (int) parser.actors.get(parentId).getChild(childNo).actorId;
  }

  private TraceParser() {
    assert VmSettings.REPLAY;
    this.parseTable = createParseTable();
    b.order(ByteOrder.LITTLE_ENDIAN);
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
    File traceFile = new File(traceName + ".trace");

    int sender = 0;
    int resolver = 0;
    int currentActor = 0;
    int ordering = 0;
    short method = 0;
    int dataId = 0;
    long startTime = System.currentTimeMillis();
    ArrayList<MessageRecord> contextMessages = null;

    Output.println("Parsing Trace ...");

    try (FileInputStream fis = new FileInputStream(traceFile);
        FileChannel channel = fis.getChannel()) {
      channel.read(b);
      b.flip(); // prepare for reading from buffer
      ActorNode current = null;

      while (channel.position() < channel.size() || b.remaining() > 0) {
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
        final int numbytes = ((type >> 4) & 3) + 1;
        boolean external = (type & 8) != 0;
        TraceRecord recordType = parseTable[type & 7];
        switch (recordType) {
          case ACTOR_CREATION:
            int newActorId = getId(numbytes);
            if (newActorId == 0) {
              assert !readMainActor : "There should be only one main actor.";
              readMainActor = true;
              if (!actors.containsKey(newActorId)) {
                actors.put(newActorId, new ActorNode(newActorId));
              }
            } else {
              if (!actors.containsKey(currentActor)) {
                actors.put(currentActor, new ActorNode(currentActor));
              }

              ActorNode node;
              if (actors.containsKey(newActorId)) {
                node = actors.get(newActorId);
              } else {
                node = new ActorNode(newActorId);
                actors.put(newActorId, node);
              }

              node.mailboxNo = ordering;
              actors.get(currentActor).addChild(node);
            }
            parsedActors++;

            assert b.position() == start + (numbytes + 1);
            break;

          case ACTOR_CONTEXT:
            /*
             * make two buckets, one for the current 65k contexs, and one for those that we
             * encounter prematurely
             * decision wher stuff goes is made based on bitset or whether the ordering byte
             * already is used in the first bucket
             * when a bucket is full, we sort the contexts and put the messages inside a queue,
             * context can then be reclaimed by GC
             */

            ordering = Short.toUnsignedInt(b.getShort());
            currentActor = getId(numbytes);

            if (!actors.containsKey(currentActor)) {
              actors.put(currentActor, new ActorNode(currentActor));
            }

            current = actors.get(currentActor);
            assert current != null;
            contextMessages = new ArrayList<>();
            current.addMessageRecords(contextMessages, ordering);
            assert b.position() == start + (numbytes + 2 + 1);
            break;
          case MESSAGE:
            parsedMessages++;
            assert contextMessages != null;
            sender = getId(numbytes);

            if (external) {
              method = b.getShort();
              dataId = b.getInt();
              contextMessages.add(new ExternalMessageRecord(sender, method, dataId));
              assert b.position() == start + (numbytes + 1 + 6);
            } else {
              contextMessages.add(new MessageRecord(sender));
              assert b.position() == start + (numbytes + 1);
            }
            break;
          case PROMISE_MESSAGE:
            parsedMessages++;
            sender = getId(numbytes);
            resolver = getId(numbytes);

            if (external) {
              method = b.getShort();
              dataId = b.getInt();
              contextMessages.add(
                  new ExternalPromiseMessageRecord(sender, resolver, method, dataId));
              assert b.position() == start + 1 + 6 + 2 * (numbytes);
            } else {
              contextMessages.add(new PromiseMessageRecord(sender, resolver));
              assert b.position() == start + 1 + 2 * (numbytes);
            }

            break;
          case SYSTEM_CALL:
            dataId = b.getInt();
            break;
          default:
            assert false;
        }
      }

    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    parseExternalData();

    long end = System.currentTimeMillis();
    Output.println("Trace with " + parsedMessages + " Messages and " + parsedActors
        + " Actors sucessfully parsed in " + (end - startTime) + "ms !");
  }

  private ByteBuffer readExternalData(final long position) {
    File traceFile = new File(traceName + ".dat");
    try (FileInputStream fis = new FileInputStream(traceFile);
        FileChannel channel = fis.getChannel()) {

      ByteBuffer bb = ByteBuffer.allocate(12);
      bb.order(ByteOrder.LITTLE_ENDIAN);

      channel.read(bb, position);
      bb.flip();

      bb.getInt(); // actorId
      bb.getInt(); // dataId
      int len = bb.getInt();

      ByteBuffer res = ByteBuffer.allocate(len);
      res.order(ByteOrder.LITTLE_ENDIAN);
      channel.read(res, position + 12);
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

    ByteBuffer bb = ByteBuffer.allocate(12);
    bb.order(ByteOrder.LITTLE_ENDIAN);

    try (FileInputStream fis = new FileInputStream(traceFile);
        FileChannel channel = fis.getChannel()) {
      while (channel.position() < channel.size()) {
        // read from file if buffer is empty

        long position = channel.position();
        bb.clear();
        channel.read(bb);
        bb.flip();

        long actor = bb.getInt();
        long dataId = bb.getInt();
        int len = bb.getInt();

        long key = (actor << 32) | dataId;
        externalDataDict.put(key, position);

        position = channel.position();
        channel.position(position + len);
      }

    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private int getId(final int numbytes) {
    switch (numbytes) {
      case 1:
        return 0 | b.get();
      case 2:
        return 0 | b.getShort();
      case 3:
        return (b.get() << 16) | b.getShort();
      case 4:
        return b.getInt();
    }
    assert false : "should not happen";
    return 0;
  }

  /**
   * Node in actor creation hierarchy.
   */
  private static class ActorNode implements Comparable<ActorNode> {
    final long                                 actorId;
    int                                        childNo;
    int                                        mailboxNo;
    boolean                                    sorted           = false;
    ArrayList<ActorNode>                       children;
    HashMap<Integer, ArrayList<MessageRecord>> bucket1          = new HashMap<>();
    HashMap<Integer, ArrayList<MessageRecord>> bucket2          = new HashMap<>();
    Queue<MessageRecord>                       expectedMessages = new java.util.LinkedList<>();
    int                                        max              = 0;
    int                                        max2             = 0;

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

    private void addMessageRecords(final ArrayList<MessageRecord> mr, final int order) {
      assert mr != null;

      if (bucket1.containsKey(order)) {
        // use bucket two
        assert !bucket2.containsKey(order);
        bucket2.put(order, mr);
        max2 = Math.max(max2, order);
        assert max2 < 0x8FFF;
      } else {
        assert !bucket2.containsKey(order);
        bucket1.put(order, mr);
        max = Math.max(max, order);
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

    @Override
    public String toString() {
      return "" + actorId + ":" + childNo;
    }
  }

  public static class MessageRecord {
    public final int sender;

    public MessageRecord(final int sender) {
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

    public ExternalMessageRecord(final int sender, final short method, final int dataId) {
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
    public int pId;

    public PromiseMessageRecord(final int sender, final int pId) {
      super(sender);
      this.pId = pId;
    }
  }

  public static class ExternalPromiseMessageRecord extends PromiseMessageRecord {
    public final int   dataId;
    public final short method;

    public ExternalPromiseMessageRecord(final int sender, final int pId, final short method,
        final int extData) {
      super(sender, pId);
      this.method = method;
      this.dataId = extData;
    }

    @Override
    public boolean isExternal() {
      return true;
    }
  }

}
