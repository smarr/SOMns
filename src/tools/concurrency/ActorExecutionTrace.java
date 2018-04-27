package tools.concurrency;

import som.interpreter.actors.EventualMessage;
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

  public static void intSystemCall(final int i, final TraceActorContextNode tracer) {
    TracingActor ta = (TracingActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    int dataId = ta.getActorId();
    ByteBuffer b = getExtDataByteBuffer(ta.getActorId(), dataId, Integer.BYTES);
    b.putInt(i);
    recordSystemCall(dataId, tracer);
    recordExternalData(b);
  }

  public static void longSystemCall(final long l, final TraceActorContextNode tracer) {
    TracingActor ta = (TracingActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    int dataId = ta.getActorId();
    ByteBuffer b = getExtDataByteBuffer(ta.getActorId(), dataId, Long.BYTES);
    b.putLong(l);
    recordSystemCall(dataId, tracer);
    recordExternalData(b);
  }

  public static void doubleSystemCall(final double d, final TraceActorContextNode tracer) {
    TracingActor ta = (TracingActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    int dataId = ta.getActorId();
    ByteBuffer b = getExtDataByteBuffer(ta.getActorId(), dataId, Double.BYTES);
    b.putDouble(d);
    recordSystemCall(dataId, tracer);
    recordExternalData(b);
  }

  public static void stringSystemCall(final String s, final TraceActorContextNode tracer) {
    TracingActor ta = (TracingActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    int dataId = ta.getActorId();
    ByteBuffer b = getExtDataByteBuffer(ta.getActorId(), dataId, s.getBytes().length);
    b.put(s.getBytes());
    recordSystemCall(dataId, tracer);
    recordExternalData(b);
  }

  public static ByteBuffer getExtDataByteBuffer(final int actor, final int dataId,
      final int size) {
    ByteBuffer bb = ByteBuffer.allocate(size + 12);
    bb.putInt(actor);
    bb.putInt(dataId);
    bb.putInt(size);
    return bb;
  }

  public static void recordExternalData(final ByteBuffer data) {
    TracingBackend.addExternalData(data);
  }

  public static class ActorTraceBuffer extends TraceBuffer {
    TracingActor currentActor;

    @Override
    protected void swapBufferWhenNotEnoughSpace(final TraceActorContextNode tracer) {
      boolean didSwap = swapStorage();
      assert didSwap;
      tracer.trace(currentActor);
    }

    public void recordActorContext(final TracingActor actor,
        final TraceActorContextNode tracer) {
      ensureSufficientSpace(7, tracer);
      currentActor = actor;
      tracer.trace(actor);
    }

    public void recordSystemCall(final int dataId, final TraceActorContextNode tracer) {
      ensureSufficientSpace(5, tracer);
      storage.putByteInt(SYSTEM_CALL, dataId);
    }
  }
}
