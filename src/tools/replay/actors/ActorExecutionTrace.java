package tools.replay.actors;

import java.util.Arrays;

import som.interpreter.actors.Actor.ActorProcessingThread;
import som.vm.Activity;
import tools.concurrency.TraceBuffer;
import tools.concurrency.TracingActivityThread;
import tools.concurrency.TracingActors.TracingActor;
import tools.replay.StringWrapper;
import tools.replay.nodes.TraceContextNode;


public class ActorExecutionTrace {
  // events
  public static final byte ACTOR_CREATION  = 0;
  public static final byte ACTOR_CONTEXT   = 1;
  public static final byte MESSAGE         = 2;
  public static final byte PROMISE_MESSAGE = 3;
  public static final byte SYSTEM_CALL     = 4;

  public static final byte CHANNEL_CREATE  = 5;
  public static final byte PROCESS_CONTEXT = 6;
  public static final byte CHANNEL_READ    = 7;
  public static final byte CHANNEL_WRITE   = 8;
  public static final byte PROCESS_CREATE  = 9;

  // flags
  public static final byte EXTERNAL_BIT = 16;

  // shifts
  public static final int SmallIdShift = 6;

  private static TracingActivityThread getThread() {
    Thread current = Thread.currentThread();
    assert current instanceof TracingActivityThread;
    return (TracingActivityThread) current;
  }

  public static void recordActivityContext(final Activity activity,
      final TraceContextNode tracer) {
    TracingActivityThread t = getThread();
    ((ActorTraceBuffer) t.getBuffer()).recordActivityContext(activity, tracer);
  }

  public static void recordSystemCall(final int dataId, final TraceContextNode tracer) {
    TracingActivityThread t = getThread();
    ((ActorTraceBuffer) t.getBuffer()).recordSystemCall(dataId, tracer);
  }

  public static void recordSystemCall(final int dataId, final TraceContextNode tracer,
      final TracingActivityThread t) {
    ((ActorTraceBuffer) t.getBuffer()).recordSystemCall(dataId, tracer);
  }

  public static void intSystemCall(final int i, final TraceContextNode tracer) {
    ActorProcessingThread t = (ActorProcessingThread) getThread();
    TracingActor ta = (TracingActor) t.getCurrentActor();
    int dataId = ta.getDataId();
    byte[] b = getExtDataByteBuffer(ta.getId(), dataId, Integer.BYTES);
    TraceBuffer.UNSAFE.putInt(
        b, TraceBuffer.BYTE_ARR_BASE_OFFSET + EXT_DATA_HEADER_SIZE, i);
    recordSystemCall(dataId, tracer);
    t.addExternalData(b);
  }

  public static void longSystemCall(final long l, final TraceContextNode tracer) {
    ActorProcessingThread t = (ActorProcessingThread) getThread();
    TracingActor ta = (TracingActor) t.getCurrentActor();
    int dataId = ta.getDataId();
    byte[] b = getExtDataByteBuffer(ta.getId(), dataId, Long.BYTES);
    TraceBuffer.UNSAFE.putLong(
        b, TraceBuffer.BYTE_ARR_BASE_OFFSET + EXT_DATA_HEADER_SIZE, l);
    recordSystemCall(dataId, tracer);
    t.addExternalData(b);
  }

  public static void doubleSystemCall(final double d, final TraceContextNode tracer) {
    ActorProcessingThread t = (ActorProcessingThread) getThread();
    TracingActor ta = (TracingActor) t.getCurrentActor();
    int dataId = ta.getDataId();
    byte[] b = getExtDataByteBuffer(ta.getId(), dataId, Double.BYTES);
    TraceBuffer.UNSAFE.putDouble(
        b, TraceBuffer.BYTE_ARR_BASE_OFFSET + EXT_DATA_HEADER_SIZE, d);
    recordSystemCall(dataId, tracer);
    t.addExternalData(b);
  }

  private static final int EXT_DATA_HEADER_SIZE = 2 * Integer.BYTES + Long.BYTES;

  public static void stringSystemCall(final String s, final TraceContextNode tracer) {
    ActorProcessingThread t = (ActorProcessingThread) getThread();
    TracingActor ta = (TracingActor) t.getCurrentActor();
    int dataId = ta.getDataId();
    recordSystemCall(dataId, tracer);
    StringWrapper sw =
        new StringWrapper(s, ta.getId(), dataId);

    t.addExternalData(sw);
  }

  public static byte[] getExtDataByteBuffer(final long actor, final int dataId,
      final int size) {
    byte[] buffer = new byte[size + EXT_DATA_HEADER_SIZE];
    Arrays.fill(buffer, (byte) -1);
    TraceBuffer.UNSAFE.putLong(buffer, TraceBuffer.BYTE_ARR_BASE_OFFSET, actor);
    TraceBuffer.UNSAFE.putInt(buffer, TraceBuffer.BYTE_ARR_BASE_OFFSET + 8, dataId);
    TraceBuffer.UNSAFE.putInt(buffer, TraceBuffer.BYTE_ARR_BASE_OFFSET + 12, size);
    return buffer;
  }

  public static byte[] getExtDataHeader(final long actor, final int dataId,
      final int size) {
    byte[] buffer = new byte[EXT_DATA_HEADER_SIZE];
    Arrays.fill(buffer, (byte) -1);
    TraceBuffer.UNSAFE.putLong(buffer, TraceBuffer.BYTE_ARR_BASE_OFFSET, actor);
    TraceBuffer.UNSAFE.putInt(buffer, TraceBuffer.BYTE_ARR_BASE_OFFSET + 8, dataId);
    TraceBuffer.UNSAFE.putInt(buffer, TraceBuffer.BYTE_ARR_BASE_OFFSET + 12, size);
    return buffer;
  }

  public static class ActorTraceBuffer extends TraceBuffer {
    Activity currentActivity;

    @Override
    protected void swapBufferWhenNotEnoughSpace(final TraceContextNode tracer) {
      swapStorage();
      if (tracer != null) {
        tracer.execute(currentActivity);
      }
    }

    public void recordActivityContext(final Activity activity,
        final TraceContextNode tracer) {
      ensureSufficientSpace(11, null); // null, because we don't need to write actor context,
                                       // and going to do it ourselves
      currentActivity = activity;
      tracer.execute(activity);
    }

    public void recordSystemCall(final int dataId, final TraceContextNode tracer) {
      ensureSufficientSpace(5, tracer);
      putByteInt(SYSTEM_CALL, dataId);
    }
  }
}
