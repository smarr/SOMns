package tools.replay;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Queue;

import som.Output;
import som.interpreter.actors.EventualMessage;
import som.vm.Activity;
import som.vm.VmSettings;
import tools.concurrency.TracingActivityThread;
import tools.concurrency.TracingActors.ReplayActor;
import tools.replay.ReplayData.ActorNode;
import tools.replay.ReplayData.EntityNode;
import tools.replay.ReplayRecord.ChannelReadRecord;
import tools.replay.ReplayRecord.ChannelWriteRecord;
import tools.replay.ReplayRecord.ExternalMessageRecord;
import tools.replay.ReplayRecord.ExternalPromiseMessageRecord;
import tools.replay.ReplayRecord.MessageRecord;
import tools.replay.ReplayRecord.PromiseMessageRecord;
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
      parser.parseTrace(true, 0, null);
      parser.parseExternalData();
    }

    ActorNode actor = (ActorNode) parser.entities.get(replayId);
    assert actor != null : "Missing expected Messages for Actor: ";
    if (!actor.contextsParsed) {
      actor.parseContexts();
    }

    return actor.getExpectedMessages();
  }

  public static synchronized Queue<ReplayRecord> getReplayEventsForEntity(
      final long replayId) {
    if (parser == null) {
      parser = new TraceParser();
      parser.parseTrace(true, 0, null);
      parser.parseExternalData();
    }

    EntityNode entity = parser.entities.get(replayId);
    assert entity != null : "Missing Entity: " + replayId;
    if (!entity.contextsParsed) {
      entity.parseContexts();
    }

    return entity.getReplayEvents();
  }

  /**
   * Determines the id a newly created entity should have according to the creating entity.
   */
  public static long getReplayId() {
    if (VmSettings.REPLAY && Thread.currentThread() instanceof TracingActivityThread) {
      TracingActivityThread t = (TracingActivityThread) Thread.currentThread();

      Activity parent = t.getActivity();
      long parentId = parent.getId();
      int childNo = parent.addChild();
      if (parser == null) {
        parser = new TraceParser();
        parser.parseTrace(true, 0, null);
        parser.parseExternalData();
      }

      assert parser.entities.containsKey(parentId) : "Parent doesn't exist";
      return parser.entities.get(parentId).getChild(childNo).entityId;
    }

    return 0;
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

  private void parseTrace(final boolean scanning, final long startOffset,
      final EntityNode context) {

    File traceFile = new File(traceName + ".trace");

    long startTime = System.currentTimeMillis();

    if (scanning) {
      Output.println("Scanning Trace ...");
    }

    try (FileInputStream fis = new FileInputStream(traceFile);
        FileChannel channel = fis.getChannel()) {

      // event context
      EntityNode currentEntity = context;
      int ordering = 0; // only used during scanning!

      b.clear();
      channel.position(startOffset);
      channel.read(b);
      b.flip(); // prepare for reading from buffer

      boolean once = true;
      boolean first = true;

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

        if (!scanning & first) {
          assert recordType == TraceRecord.ACTOR_CONTEXT
              || recordType == TraceRecord.PROCESS_CONTEXT;
        }
        first = false;
        switch (recordType) {
          case ACTOR_CREATION:
          case CHANNEL_CREATION:
          case PROCESS_CREATION:
            long newEntityId = getId(numbytes);

            if (scanning) {
              if (newEntityId == 0) {
                assert !readMainActor : "There should be only one main actor.";
                readMainActor = true;
              }

              EntityNode newEntity = getOrCreateEntityEntry(recordType, newEntityId);
              newEntity.ordering = ordering;
              currentEntity.addChild(newEntity);
              parsedEntities++;
            }
            assert b.position() == start + RecordEventNodes.ONE_EVENT_SIZE;
            break;

          case ACTOR_CONTEXT:
          case PROCESS_CONTEXT:
            if (scanning) {
              ordering = Short.toUnsignedInt(b.getShort());
              long currentEntityId = getId(Long.BYTES);
              currentEntity = getOrCreateEntityEntry(recordType, currentEntityId);
              assert currentEntity != null;
              currentEntity.registerContext(ordering,
                  (channel.position() - b.remaining() - 11));
              assert b.position() == start + 11;
            } else {
              if (once) {
                ordering = Short.toUnsignedInt(b.getShort());
                getId(Long.BYTES);
                once = false;
              } else {
                // When we are not scanning for contexts, this means that the context we wanted
                // to process is over.
                return;
              }
            }

            break;

          case MESSAGE:
            parsedMessages++;
            sender = getId(numbytes);

            if (external) {
              edat = b.getLong();
              if (!scanning) {
                method = (short) (edat >> 32);
                dataId = (int) edat;
                ((ActorNode) currentEntity).addMessageRecord(
                    new ExternalMessageRecord(sender, method, dataId));
              }
              assert b.position() == start + RecordEventNodes.TWO_EVENT_SIZE;
              continue;
            }

            if (!scanning) {
              ((ActorNode) currentEntity).addMessageRecord(new MessageRecord(sender));
            }
            assert b.position() == start + RecordEventNodes.ONE_EVENT_SIZE;
            break;
          case PROMISE_MESSAGE:
            parsedMessages++;
            sender = getId(numbytes);
            resolver = getId(numbytes);

            if (external) {
              edat = b.getLong();
              method = (short) (edat >> 32);
              dataId = (int) edat;
              if (!scanning) {
                ((ActorNode) currentEntity).addMessageRecord(
                    new ExternalPromiseMessageRecord(sender, resolver, method, dataId));
              }
              assert b.position() == start + RecordEventNodes.THREE_EVENT_SIZE;
              continue;
            }
            if (!scanning) {
              ((ActorNode) currentEntity).addMessageRecord(
                  new PromiseMessageRecord(sender, resolver));
            }
            assert b.position() == start + RecordEventNodes.TWO_EVENT_SIZE;

            break;
          case SYSTEM_CALL:
            dataId = b.getInt();
            break;
          case CHANNEL_READ:
            long channelRId = b.getLong();
            long nread = b.getLong();
            currentEntity.addReplayEvent(new ChannelReadRecord(channelRId, nread));
            assert b.position() == start + RecordEventNodes.TWO_EVENT_SIZE;
            break;
          case CHANNEL_WRITE:
            long channelWId = b.getLong();
            long nwrite = b.getLong();
            currentEntity.addReplayEvent(new ChannelWriteRecord(channelWId, nwrite));
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

    if (scanning) {
      long end = System.currentTimeMillis();
      Output.println("Trace with " + parsedMessages + " Messages and " + parsedEntities
          + " Actors sucessfully scanned in " + (end - startTime) + "ms !");
    }
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
        default:
          newNode = new EntityNode(entityId);
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

  protected static void processContext(final long location, final EntityNode context) {
    parser.parseTrace(false, location, context);
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
}
