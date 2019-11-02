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

import som.Output;
import som.interpreter.actors.EventualMessage;
import som.vm.Activity;
import som.vm.VmSettings;
import tools.concurrency.TracingActivityThread;
import tools.concurrency.TracingActors.ReplayActor;
import tools.replay.ReplayData.EntityNode;
import tools.replay.ReplayData.Subtrace;
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

  public LinkedList<ReplayRecord> getReplayEventsForEntity(final long replayId) {
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
    TraceRecord[] result = new TraceRecord[22];

    result[TraceRecord.ACTIVITY_CREATION.value] = TraceRecord.ACTIVITY_CREATION;
    result[TraceRecord.ACTIVITY_CONTEXT.value] = TraceRecord.ACTIVITY_CONTEXT;
    result[TraceRecord.MESSAGE.value] = TraceRecord.MESSAGE;
    result[TraceRecord.PROMISE_MESSAGE.value] = TraceRecord.PROMISE_MESSAGE;
    result[TraceRecord.SYSTEM_CALL.value] = TraceRecord.SYSTEM_CALL;
    result[TraceRecord.CHANNEL_READ.value] = TraceRecord.CHANNEL_READ;
    result[TraceRecord.CHANNEL_WRITE.value] = TraceRecord.CHANNEL_WRITE;

    result[TraceRecord.LOCK_ISLOCKED.value] = TraceRecord.LOCK_ISLOCKED;
    result[TraceRecord.CONDITION_TIMEOUT.value] =
        TraceRecord.CONDITION_TIMEOUT;

    result[TraceRecord.CHANNEL_READ.value] = TraceRecord.CHANNEL_READ;
    result[TraceRecord.CHANNEL_WRITE.value] = TraceRecord.CHANNEL_WRITE;
    result[TraceRecord.LOCK_LOCK.value] = TraceRecord.LOCK_LOCK;
    result[TraceRecord.CONDITION_WAKEUP.value] = TraceRecord.CONDITION_WAKEUP;
    result[TraceRecord.PROMISE_RESOLUTION.value] = TraceRecord.PROMISE_RESOLUTION;
    result[TraceRecord.PROMISE_CHAINED.value] = TraceRecord.PROMISE_CHAINED;
    result[TraceRecord.PROMISE_RESOLUTION_END.value] = TraceRecord.PROMISE_RESOLUTION_END;
    result[TraceRecord.TRANSACTION_COMMIT.value] = TraceRecord.TRANSACTION_COMMIT;

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
    } catch (Throwable e) {
      e.printStackTrace();
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

    final int start = b.position();
    final byte type = b.get();
    final int numbytes = Long.BYTES;
    // boolean external = (type & TraceRecord.EXTERNAL_BIT) != 0;

    TraceRecord recordType = parseTable[type];// & (TraceRecord.EXTERNAL_BIT - 1)];

    if (!scanning && first) {
      assert recordType == TraceRecord.ACTIVITY_CONTEXT;
    }

    switch (recordType) {
      case ACTIVITY_CREATION:
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

      case ACTIVITY_CONTEXT:
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
      case SYSTEM_CALL:
        b.getInt();
        break;

      case CONDITION_TIMEOUT:
      case LOCK_ISLOCKED:
      case PROMISE_MESSAGE:
      case PROMISE_RESOLUTION:
      case PROMISE_RESOLUTION_END:
      case PROMISE_CHAINED:
      case MESSAGE:
      case CHANNEL_READ:
      case CHANNEL_WRITE:
      case LOCK_LOCK:
      case CONDITION_WAKEUP:
      case TRANSACTION_COMMIT:
        long eventData = b.getLong();
        if (!scanning) {
          ctx.currentEntity.addReplayEvent(
              new ReplayRecord(eventData, recordType));

        }
        assert b.position() == start + RecordEventNodes.ONE_EVENT_SIZE;
        break;
      default:
        Output.println("MISSING");
        assert false;
    }

    return false;
  }

  private EntityNode getOrCreateEntityEntry(final TraceRecord type,
      final long entityId) {
    if (entities.containsKey(entityId)) {
      return entities.get(entityId);
    } else {
      EntityNode newNode = new EntityNode(entityId);
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
