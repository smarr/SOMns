package tools.concurrency;

import tools.replay.actors.ActorExecutionTrace.ActorTraceBuffer;
import tools.replay.nodes.TraceContextNode;
import tools.replay.nodes.TraceContextNodeGen;
import tools.replay.nodes.TraceNode;


public final class RecordEventNodes {
  public static final int ONE_EVENT_SIZE   = 1 + Long.BYTES;
  public static final int TWO_EVENT_SIZE   = 1 + (2 * Long.BYTES);
  public static final int THREE_EVENT_SIZE = 1 + (3 * Long.BYTES);

  public static class RecordOneEvent extends TraceNode {
    @Child TraceContextNode tracer = TraceContextNodeGen.create();

    private final byte eventType;

    public RecordOneEvent(final byte eventType) {
      this.eventType = eventType;
    }

    private ActorTraceBuffer getStorage(final int entrySize) {
      ActorTraceBuffer buffer = getCurrentBuffer();
      buffer.ensureSufficientSpace(entrySize, tracer);
      return buffer;
    }

    // TODO make the context recording more general
    public void record(final long id) {
      ActorTraceBuffer storage = getStorage(ONE_EVENT_SIZE);
      int pos = storage.position();

      storage.putByteAt(pos, eventType);
      storage.putLongAt(pos + 1, id);

      storage.position(pos + ONE_EVENT_SIZE);
    }
  }

  public static class RecordTwoEvent extends TraceNode {
    @Child TraceContextNode tracer = TraceContextNodeGen.create();

    private final byte eventType;

    public RecordTwoEvent(final byte eventType) {
      this.eventType = eventType;
    }

    private ActorTraceBuffer getStorage(final int entrySize) {
      ActorTraceBuffer buffer = getCurrentBuffer();
      buffer.ensureSufficientSpace(entrySize, tracer);
      return buffer;
    }

    // TODO make the context recording more general
    public void record(final long id1, final long id2) {
      ActorTraceBuffer storage = getStorage(TWO_EVENT_SIZE);
      int pos = storage.position();

      storage.putByteAt(pos, eventType);
      storage.putLongAt(pos + 1, id1);
      storage.putLongAt(pos + 1 + Long.BYTES, id2);

      storage.position(pos + TWO_EVENT_SIZE);
    }
  }

  public static class RecordThreeEvent extends TraceNode {
    @Child TraceContextNode tracer = TraceContextNodeGen.create();

    private final byte eventType;

    public RecordThreeEvent(final byte eventType) {
      this.eventType = eventType;
    }

    private ActorTraceBuffer getStorage(final int entrySize) {
      ActorTraceBuffer buffer = getCurrentBuffer();
      buffer.ensureSufficientSpace(entrySize, tracer);
      return buffer;
    }

    // TODO make the context recording more general
    public void record(final long id1, final long id2, final long id3) {
      ActorTraceBuffer storage = getStorage(TWO_EVENT_SIZE);
      int pos = storage.position();

      storage.putByteAt(pos, eventType);
      storage.putLongAt(pos + 1, id1);
      storage.putLongAt(pos + 1 + Long.BYTES, id2);

      storage.position(pos + TWO_EVENT_SIZE);
    }
  }
}
