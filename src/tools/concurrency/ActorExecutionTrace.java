package tools.concurrency;

import java.util.Arrays;

import som.interpreter.actors.Actor.ActorProcessingThread;
import som.vmobjects.SArray.SImmutableArray;
import tools.concurrency.TracingActors.TracingActor;
import tools.concurrency.nodes.TraceActorContextNode;


public class ActorExecutionTrace {
  // events
  public static final byte ACTOR_CREATION  = 0;
  public static final byte ACTOR_CONTEXT   = 1;
  public static final byte MESSAGE         = 2;
  public static final byte PROMISE_MESSAGE = 3;
  public static final byte SYSTEM_CALL     = 4;
  // flags
  public static final byte EXTERNAL_BIT = 8;

  private static TracingActivityThread getThread() {
    Thread current = Thread.currentThread();
    assert current instanceof TracingActivityThread;
    return (TracingActivityThread) current;
  }

  public static void recordActorContext(final TracingActor actor,
      final TraceActorContextNode tracer) {
    TracingActivityThread t = getThread();
    ((ActorTraceBuffer) t.getBuffer()).recordActorContext(actor, tracer);
  }

  public static void recordSystemCall(final int dataId, final TraceActorContextNode tracer) {
    TracingActivityThread t = getThread();
    ((ActorTraceBuffer) t.getBuffer()).recordSystemCall(dataId, tracer);
  }

  public static void recordSystemCall(final int dataId, final TraceActorContextNode tracer,
      final TracingActivityThread t) {
    ((ActorTraceBuffer) t.getBuffer()).recordSystemCall(dataId, tracer);
  }

  public static void intSystemCall(final int i, final TraceActorContextNode tracer) {
    ActorProcessingThread t = (ActorProcessingThread) getThread();
    TracingActor ta = (TracingActor) t.getCurrentActor();
    int dataId = ta.getDataId();
    byte[] b = getExtDataByteBuffer(ta.getActorId(), dataId, Integer.BYTES);
    TraceBuffer.UNSAFE.putInt(
        b, TraceBuffer.BYTE_ARR_BASE_OFFSET + EXT_DATA_HEADER_SIZE, i);
    recordSystemCall(dataId, tracer);
    t.addExternalData(b);
  }

  public static void longSystemCall(final long l, final TraceActorContextNode tracer) {
    ActorProcessingThread t = (ActorProcessingThread) getThread();
    TracingActor ta = (TracingActor) t.getCurrentActor();
    int dataId = ta.getDataId();
    byte[] b = getExtDataByteBuffer(ta.getActorId(), dataId, Long.BYTES);
    TraceBuffer.UNSAFE.putLong(
        b, TraceBuffer.BYTE_ARR_BASE_OFFSET + EXT_DATA_HEADER_SIZE, l);
    recordSystemCall(dataId, tracer);
    t.addExternalData(b);
  }

  public static void doubleSystemCall(final double d, final TraceActorContextNode tracer) {
    ActorProcessingThread t = (ActorProcessingThread) getThread();
    TracingActor ta = (TracingActor) t.getCurrentActor();
    int dataId = ta.getDataId();
    byte[] b = getExtDataByteBuffer(ta.getActorId(), dataId, Double.BYTES);
    TraceBuffer.UNSAFE.putDouble(
        b, TraceBuffer.BYTE_ARR_BASE_OFFSET + EXT_DATA_HEADER_SIZE, d);
    recordSystemCall(dataId, tracer);
    t.addExternalData(b);
  }

  public static void stringSystemCall(final String s, final TraceActorContextNode tracer) {
    ActorProcessingThread t = (ActorProcessingThread) getThread();
    TracingActor ta = (TracingActor) t.getCurrentActor();
    int dataId = ta.getDataId();
    recordSystemCall(dataId, tracer);
    StringWrapper sw =
        new StringWrapper(s, ta.getActorId(), dataId);

    t.addExternalData(sw);
  }

  private static final int EXT_DATA_HEADER_SIZE = 3 * 4;

  public static byte[] getExtDataByteBuffer(final int actor, final int dataId,
      final int size) {
    byte[] buffer = new byte[size + EXT_DATA_HEADER_SIZE];
    Arrays.fill(buffer, (byte) -1);
    TraceBuffer.UNSAFE.putInt(buffer, TraceBuffer.BYTE_ARR_BASE_OFFSET, actor);
    TraceBuffer.UNSAFE.putInt(buffer, TraceBuffer.BYTE_ARR_BASE_OFFSET + 4, dataId);
    TraceBuffer.UNSAFE.putInt(buffer, TraceBuffer.BYTE_ARR_BASE_OFFSET + 8, size);
    return buffer;
  }

  public static byte[] getExtDataHeader(final int actor, final int dataId,
      final int size) {
    byte[] buffer = new byte[EXT_DATA_HEADER_SIZE];
    Arrays.fill(buffer, (byte) -1);
    TraceBuffer.UNSAFE.putInt(buffer, TraceBuffer.BYTE_ARR_BASE_OFFSET, actor);
    TraceBuffer.UNSAFE.putInt(buffer, TraceBuffer.BYTE_ARR_BASE_OFFSET + 4, dataId);
    TraceBuffer.UNSAFE.putInt(buffer, TraceBuffer.BYTE_ARR_BASE_OFFSET + 8, size);
    return buffer;
  }

  public static class ActorTraceBuffer extends TraceBuffer {
    TracingActor currentActor;

    @Override
    protected void swapBufferWhenNotEnoughSpace(final TraceActorContextNode tracer) {
      swapStorage();
      if (tracer != null) {
        tracer.trace(currentActor);
      }
    }

    public void recordActorContext(final TracingActor actor,
        final TraceActorContextNode tracer) {
      ensureSufficientSpace(7, null); // null, because we don't need to write actor context,
                                      // and going to do it ourselves
      currentActor = actor;
      tracer.trace(actor);
    }

    public void recordSystemCall(final int dataId, final TraceActorContextNode tracer) {
      ensureSufficientSpace(5, tracer);
      putByteInt(SYSTEM_CALL, dataId);
    }
  }

  public static class StringWrapper {
    final String s;
    final int    actorId;
    final int    dataId;

    public StringWrapper(final String s, final int actorId, final int dataId) {
      super();
      this.s = s;
      this.actorId = actorId;
      this.dataId = dataId;
    }
  }

  public static class TwoDArrayWrapper {
    final SImmutableArray ia;
    final int             actorId;
    final int             dataId;

    public TwoDArrayWrapper(final SImmutableArray ia, final int actorId, final int dataId) {
      super();
      this.ia = ia;
      this.actorId = actorId;
      this.dataId = dataId;
    }
  }
}
