package tools.replay;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import som.Output;
import som.interpreter.actors.EventualMessage;
import som.vm.Activity;
import som.vm.VmSettings;
import tools.concurrency.TracingActivityThread;
import tools.concurrency.TracingActors.ReplayActor;
import tools.replay.ReplayData.ActorNode;
import tools.replay.ReplayData.EntityNode;
import tools.replay.ReplayData.Subtrace;
import tools.replay.ReplayRecord.AwaitTimeoutRecord;
import tools.replay.ReplayRecord.ExternalMessageRecord;
import tools.replay.ReplayRecord.ExternalPromiseMessageRecord;
import tools.replay.ReplayRecord.IsLockedRecord;
import tools.replay.ReplayRecord.MessageRecord;
import tools.replay.ReplayRecord.NumberedPassiveRecord;
import tools.replay.ReplayRecord.PromiseMessageRecord;
import tools.replay.nodes.RecordEventNodes;


public final class TraceParser implements Closeable {

  private final File            traceFile;
  private final FileInputStream traceInputStream;
  private final String          traceName;
  private final FileChannel     traceChannel;

  private final HashMap<Long, EntityNode> entities = new HashMap<>();

  private static final TraceRecord[] parseTable = createParseTable();

  private final int standardBufferSize;

  public TraceParser(final String traceName, final int standardBufferSize) {
    this.traceName = traceName + (VmSettings.SNAPSHOTS_ENABLED ? ".0" : "");
    traceFile = new File(this.traceName + ".trace");

    try {
      traceInputStream = new FileInputStream(traceFile);
      traceChannel = traceInputStream.getChannel();
    } catch (FileNotFoundException e) {
      throw new RuntimeException(
          "Attempted to open trace file '" + traceFile.getAbsolutePath() +
              "', but failed. ",
          e);
    }

    this.standardBufferSize = standardBufferSize;
  }

  public TraceParser(final String traceName) {
    this(traceName, VmSettings.BUFFER_SIZE);
  }

  public void initialize() {
    parseTrace(true, null, null);
    parseExternalData();
  }

  @Override
  public void close() throws IOException {
    traceInputStream.close();
  }

  protected HashMap<Long, EntityNode> getEntities() {
    return entities;
  }

  public ByteBuffer getExternalData(final long actorId, final int dataId) {
    long pos = entities.get(actorId).externalData.get(dataId);
    return readExternalData(pos);
  }

  public int getIntegerSysCallResult() {
    ReplayActor ra = (ReplayActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    ByteBuffer bb = getExternalData(ra.getId(), ra.getDataId());
    return bb.getInt();
  }

  public long getLongSysCallResult() {
    ReplayActor ra = (ReplayActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    ByteBuffer bb = getExternalData(ra.getId(), ra.getDataId());
    return bb.getLong();
  }

  public double getDoubleSysCallResult() {
    ReplayActor ra = (ReplayActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    ByteBuffer bb = getExternalData(ra.getId(), ra.getDataId());
    return bb.getDouble();
  }

  public String getStringSysCallResult() {
    ReplayActor ra = (ReplayActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    ByteBuffer bb = getExternalData(ra.getId(), ra.getDataId());
    return new String(bb.array());
  }

  public LinkedList<MessageRecord> getExpectedMessages(final long replayId) {
    ActorNode actor = (ActorNode) entities.get(replayId);
    assert actor != null : "Missing expected Messages for Actor: ";
    return actor.getExpectedMessages();
  }

  public Queue<ReplayRecord> getReplayEventsForEntity(final long replayId) {
    EntityNode entity = entities.get(replayId);
    assert !entity.retrieved;
    entity.retrieved = true;
    assert entity != null : "Missing Entity: " + replayId;
    return entity.getReplayEvents();
  }

  public boolean getMoreEventsForEntity(final long replayId) {
    EntityNode entity = entities.get(replayId);
    assert entity != null : "Missing Entity: " + replayId;
    return entity.parseContexts(this);
  }

  public int getNumberOfSubtraces(final long replayId) {
    EntityNode entity = entities.get(replayId);
    assert entity != null : "Missing Entity: " + replayId;
    return entity.subtraces.size();
  }

  /**
   * Determines the id a newly created entity should have according to the creating entity.
   */
  public long getReplayId() {
    if (VmSettings.REPLAY && Thread.currentThread() instanceof TracingActivityThread) {
      TracingActivityThread t = (TracingActivityThread) Thread.currentThread();

      Activity parent = t.getActivity();
      long parentId = parent.getId();
      int childNo = parent.addChild();

      assert entities.containsKey(parentId) : "Parent doesn't exist";
      return entities.get(parentId).getChild(childNo).entityId;
    }

    return 0;
  }

  private static TraceRecord[] createParseTable() {
    TraceRecord[] result = new TraceRecord[19];

    result[TraceRecord.ACTOR_CREATION.value] = TraceRecord.ACTOR_CREATION;
    result[TraceRecord.ACTOR_CONTEXT.value] = TraceRecord.ACTOR_CONTEXT;
    result[TraceRecord.MESSAGE.value] = TraceRecord.MESSAGE;
    result[TraceRecord.PROMISE_MESSAGE.value] = TraceRecord.PROMISE_MESSAGE;
    result[TraceRecord.SYSTEM_CALL.value] = TraceRecord.SYSTEM_CALL;
    result[TraceRecord.CHANNEL_CREATION.value] = TraceRecord.CHANNEL_CREATION;
    result[TraceRecord.PROCESS_CONTEXT.value] = TraceRecord.PROCESS_CONTEXT;
    result[TraceRecord.CHANNEL_READ.value] = TraceRecord.CHANNEL_READ;
    result[TraceRecord.CHANNEL_WRITE.value] = TraceRecord.CHANNEL_WRITE;
    result[TraceRecord.PROCESS_CREATION.value] = TraceRecord.PROCESS_CREATION;

    result[TraceRecord.LOCK_ISLOCKED.value] = TraceRecord.LOCK_ISLOCKED;
    result[TraceRecord.CONDITION_AWAITTIMEOUT_RES.value] =
        TraceRecord.CONDITION_AWAITTIMEOUT_RES;

    result[TraceRecord.LOCK_CREATE.value] = TraceRecord.LOCK_CREATE;
    result[TraceRecord.CONDITION_CREATE.value] = TraceRecord.CONDITION_CREATE;
    result[TraceRecord.CHANNEL_READ.value] = TraceRecord.CHANNEL_READ;
    result[TraceRecord.CHANNEL_WRITE.value] = TraceRecord.CHANNEL_WRITE;
    result[TraceRecord.LOCK_LOCK.value] = TraceRecord.LOCK_LOCK;
    result[TraceRecord.CONDITION_WAIT.value] = TraceRecord.CONDITION_WAIT;
    result[TraceRecord.CONDITION_AWAITTIMEOUT.value] = TraceRecord.CONDITION_AWAITTIMEOUT;
    result[TraceRecord.CONDITION_WAKEUP.value] = TraceRecord.CONDITION_WAKEUP;
    result[TraceRecord.CONDITION_SIGNALALL.value] = TraceRecord.CONDITION_SIGNALALL;

    return result;
  }

  private static final class EventParseContext {
    int        ordering;     // only used during scanning!
    EntityNode currentEntity;
    boolean    once;
    boolean    readMainActor;

    Subtrace entityLocation;

    final long startTime      = System.currentTimeMillis();
    long       parsedMessages = 0;
    long       parsedEntities = 0;

    EventParseContext(final EntityNode context) {
      this.currentEntity = context;
      this.once = true;
      this.readMainActor = false;
    }
  }

  protected void parseTrace(final boolean scanning, final Subtrace loc,
      final EntityNode context) {
    final int parseLength = loc == null || loc.length == 0 ? standardBufferSize
        : Math.min(standardBufferSize, (int) loc.length);

    ByteBuffer b = ByteBuffer.allocate(parseLength);
    b.order(ByteOrder.LITTLE_ENDIAN);

    if (scanning) {
      Output.println("Scanning Trace ...");
    }

    EventParseContext ctx = new EventParseContext(context);

    try {
      b.clear();
      int nextReadPosition = loc == null ? 0 : (int) loc.startOffset;
      int startPosition = nextReadPosition;

      nextReadPosition = readFromChannel(b, nextReadPosition);

      final boolean readAll = loc != null && loc.length == nextReadPosition - startPosition;

      boolean first = true;

      while ((!readAll && nextReadPosition < traceChannel.size()) || b.hasRemaining()) {
        if (!readAll
            && b.remaining() < RecordEventNodes.THREE_EVENT_SIZE /* longest possible event */
            && nextReadPosition < traceChannel.size()) {
          int remaining = b.remaining();
          startPosition += b.position();
          b.compact();
          nextReadPosition = readFromChannel(b, startPosition + remaining);
        }

        boolean done = readTrace(first, scanning, b, ctx, startPosition, nextReadPosition);
        if (done) {
          return;
        }

        if (first) {
          first = false;
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    if (scanning) {
      long end = System.currentTimeMillis();
      Output.println("Trace with " + ctx.parsedMessages + " Messages and " + ctx.parsedEntities
          + " Actors sucessfully scanned in " + (end - ctx.startTime) + "ms !");
    }
  }

  private int readFromChannel(final ByteBuffer b, int currentPosition) throws IOException {
    int numBytesRead = traceChannel.read(b, currentPosition);
    currentPosition += numBytesRead;
    b.flip();
    return currentPosition;
  }

  private boolean readTrace(final boolean first, final boolean scanning, final ByteBuffer b,
      final EventParseContext ctx, final int startPosition, final int nextReadPosition) {
    long sender = 0;
    long resolver = 0;
    long edat = 0;
    short method = 0;
    int dataId = 0;

    final int start = b.position();
    final byte type = b.get();
    final int numbytes = Long.BYTES;
    boolean external = (type & TraceRecord.EXTERNAL_BIT) != 0;

    TraceRecord recordType = parseTable[type & (TraceRecord.EXTERNAL_BIT - 1)];

    if (!scanning & first) {
      assert recordType == TraceRecord.ACTOR_CONTEXT
          || recordType == TraceRecord.PROCESS_CONTEXT;
    }

    switch (recordType) {
      case ACTOR_CREATION:
      case CHANNEL_CREATION:
      case PROCESS_CREATION:
      case CONDITION_CREATE:
      case LOCK_CREATE:
        long newEntityId = getId(b, numbytes);

        if (scanning) {
          if (newEntityId == 0) {
            assert !ctx.readMainActor : "There should be only one main actor.";
            ctx.readMainActor = true;
          }

          EntityNode newEntity = getOrCreateEntityEntry(recordType, newEntityId);
          newEntity.ordering = ctx.ordering;
          ctx.currentEntity.addChild(newEntity);
          ctx.parsedEntities++;
        }
        assert b.position() == start + RecordEventNodes.ONE_EVENT_SIZE;
        break;

      case ACTOR_CONTEXT:
      case PROCESS_CONTEXT:
        if (scanning) {
          if (ctx.currentEntity != null) {
            ctx.entityLocation.length = startPosition + start - ctx.entityLocation.startOffset;
          }
          ctx.ordering = Short.toUnsignedInt(b.getShort());
          long currentEntityId = getId(b, Long.BYTES);
          ctx.currentEntity = getOrCreateEntityEntry(recordType, currentEntityId);
          assert ctx.currentEntity != null;
          Subtrace loc =
              ctx.currentEntity.registerContext(ctx.ordering, startPosition + start);

          assert b.position() == start + 11;
          ctx.entityLocation = loc;
        } else {
          if (ctx.once) {
            ctx.ordering = Short.toUnsignedInt(b.getShort());
            getId(b, Long.BYTES);
            ctx.once = false;
          } else {
            // When we are not scanning for contexts, this means that the context we wanted
            // to process is over.
            return true;
          }
        }

        break;

      case MESSAGE:
        ctx.parsedMessages++;
        sender = getId(b, numbytes);

        if (external) {
          edat = b.getLong();
          if (!scanning) {
            method = (short) (edat >> 32);
            dataId = (int) edat;
            ((ActorNode) ctx.currentEntity).addMessageRecord(
                new ExternalMessageRecord(sender, method, dataId));
          }
          assert b.position() == start + RecordEventNodes.TWO_EVENT_SIZE;
          return false;
        }

        if (!scanning) {
          ((ActorNode) ctx.currentEntity).addMessageRecord(new MessageRecord(sender));
        }
        assert b.position() == start + RecordEventNodes.ONE_EVENT_SIZE;
        break;
      case PROMISE_MESSAGE:
        ctx.parsedMessages++;
        sender = getId(b, numbytes);
        resolver = getId(b, numbytes);

        if (external) {
          edat = b.getLong();
          method = (short) (edat >> 32);
          dataId = (int) edat;
          if (!scanning) {
            ((ActorNode) ctx.currentEntity).addMessageRecord(
                new ExternalPromiseMessageRecord(sender, resolver, method, dataId));
          }
          assert b.position() == start + RecordEventNodes.THREE_EVENT_SIZE;
          return false;
        }
        if (!scanning) {
          ((ActorNode) ctx.currentEntity).addMessageRecord(
              new PromiseMessageRecord(sender, resolver));
        }
        assert b.position() == start + RecordEventNodes.TWO_EVENT_SIZE;

        break;
      case SYSTEM_CALL:
        dataId = b.getInt();
        break;

      case LOCK_ISLOCKED:
        long passiveId = b.getLong();
        long result = b.getLong();
        ctx.currentEntity.addReplayEvent(new IsLockedRecord(passiveId, result));
        assert b.position() == start + RecordEventNodes.TWO_EVENT_SIZE;
        break;
      case CONDITION_AWAITTIMEOUT_RES:
        long isSignaled = b.getLong();
        ctx.currentEntity.addReplayEvent(new AwaitTimeoutRecord(isSignaled));
        assert b.position() == start + RecordEventNodes.ONE_EVENT_SIZE;
        break;
      case CHANNEL_READ:
      case CHANNEL_WRITE:
      case LOCK_LOCK:
      case CONDITION_WAIT:
      case CONDITION_AWAITTIMEOUT:
      case CONDITION_WAKEUP:
      case CONDITION_SIGNALALL:
        long passiveEntityId = b.getLong();
        long eventNo = b.getLong();
        ctx.currentEntity.addReplayEvent(new NumberedPassiveRecord(passiveEntityId, eventNo));
        assert b.position() == start + RecordEventNodes.TWO_EVENT_SIZE;
        break;
      default:
        assert false;
    }

    return false;
  }

  private EntityNode getOrCreateEntityEntry(final TraceRecord type,
      final long entityId) {
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

  protected void processContext(final Subtrace location, final EntityNode context) {
    parseTrace(false, location, context);
  }

  private static long getId(final ByteBuffer b, final int numbytes) {
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
