package tools.replay.nodes;

import tools.replay.TraceRecord;
import tools.replay.actors.UniformExecutionTrace.UniformTraceBuffer;


public final class RecordEventNodes {
  public static final int ONE_EVENT_SIZE   = 1 + Long.BYTES;
  public static final int THREE_EVENT_SIZE = 1 + Long.BYTES + Long.BYTES + Long.BYTES;

  public static class RecordOneEvent extends TraceNode {
    @Child TraceContextNode tracer = TraceContextNodeGen.create();

    private final TraceRecord eventType;

    public RecordOneEvent(final TraceRecord eventType) {
      this.eventType = eventType;
    }

    private UniformTraceBuffer getStorage(final int entrySize) {
      UniformTraceBuffer buffer = getCurrentBuffer();
      buffer.ensureSufficientSpace(entrySize, tracer);
      return buffer;
    }

    public void record(final long id) {
      UniformTraceBuffer storage = getStorage(ONE_EVENT_SIZE);
      int pos = storage.position();

      assert id >= 0;
      storage.putByteAt(pos, eventType.value);
      storage.putLongAt(pos + 1, id);

      storage.position(pos + ONE_EVENT_SIZE);
    }
  }
}
