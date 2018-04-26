package tools.concurrency;

import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.ExternalMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.SPromise.STracingPromise;
import som.vm.VmSettings;
import tools.concurrency.TracingActors.TracingActor;
import tools.concurrency.nodes.TraceActorContext;


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
      final TraceActorContext tracer) {
    TracingActivityThread t = getThread();
    ((ActorTraceBuffer) t.getBuffer()).recordActorContext(actor, tracer);
  }

  public static void recordMessage(final EventualMessage msg, final TraceActorContext tracer) {
    TracingActivityThread t = getThread();
    ActorTraceBuffer atb = ((ActorTraceBuffer) t.getBuffer());
    if (msg instanceof ExternalMessage) {
      ExternalMessage em = (ExternalMessage) msg;
      if (msg instanceof PromiseMessage) {
        atb.recordExternalPromiseMessage(msg.getSender().getActorId(),
            ((STracingPromise) ((PromiseMessage) msg).getPromise()).getResolvingActor(),
            em.getMethod(), em.getDataId(), tracer);
      } else {
        atb.recordExternalMessage(msg.getSender().getActorId(), em.getMethod(),
            em.getDataId(), tracer);
      }
    } else {
      if (msg instanceof PromiseMessage) {
        atb.recordPromiseMessage(msg.getSender().getActorId(),
            ((STracingPromise) ((PromiseMessage) msg).getPromise()).getResolvingActor(),
            tracer);
      } else {
        atb.recordMessage(msg.getSender().getActorId(), tracer);
      }
    }
  }

  public static void recordSystemCall(final int dataId, final TraceActorContext tracer) {
    TracingActivityThread t = getThread();
    ((ActorTraceBuffer) t.getBuffer()).recordSystemCall(dataId, tracer);
  }

  public static void intSystemCall(final int i, final TraceActorContext tracer) {
    TracingActor ta = (TracingActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    int dataId = ta.getActorId();
    ByteBuffer b = getExtDataByteBuffer(ta.getActorId(), dataId, Integer.BYTES);
    b.putInt(i);
    recordSystemCall(dataId, tracer);
    recordExternalData(b);
  }

  public static void longSystemCall(final long l, final TraceActorContext tracer) {
    TracingActor ta = (TracingActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    int dataId = ta.getActorId();
    ByteBuffer b = getExtDataByteBuffer(ta.getActorId(), dataId, Long.BYTES);
    b.putLong(l);
    recordSystemCall(dataId, tracer);
    recordExternalData(b);
  }

  public static void doubleSystemCall(final double d, final TraceActorContext tracer) {
    TracingActor ta = (TracingActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    int dataId = ta.getActorId();
    ByteBuffer b = getExtDataByteBuffer(ta.getActorId(), dataId, Double.BYTES);
    b.putDouble(d);
    recordSystemCall(dataId, tracer);
    recordExternalData(b);
  }

  public static void stringSystemCall(final String s, final TraceActorContext tracer) {
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
    protected void swapBufferWhenNotEnoughSpace(final TraceActorContext tracer) {
      boolean didSwap = swapStorage();
      assert didSwap;
      tracer.execute(currentActor);
    }

    static int getUsedBytes(final int id) {
      if (id >= 0) {
        if (id <= 0xFF) {
          return 0;
        } else if (id <= 0xFFFF) {
          return 1;
        } else if (id <= 0xFFFFFF) {
          return 2;
        }
      }
      return 3;
    }

    public void recordActorContext(final TracingActor actor, final TraceActorContext tracer) {
      ensureSufficientSpace(7, tracer);
      currentActor = actor;
      tracer.execute(actor);
    }

    public void recordMessage(final int senderId, final TraceActorContext tracer) {
      ensureSufficientSpace(5, tracer);
      if (VmSettings.TRACE_SMALL_IDS) {
        int usedBytes = getUsedBytes(senderId);
        storage.put((byte) (MESSAGE | (usedBytes << 4)));
        writeId(usedBytes, senderId);
      } else {
        storage.putByteInt((byte) (MESSAGE | (3 << 4)), senderId);
      }
    }

    public void recordPromiseMessage(final int senderId, final int resolverId,
        final TraceActorContext tracer) {
      ensureSufficientSpace(9, tracer);
      int usedBytes = Math.max(getUsedBytes(resolverId), getUsedBytes(senderId));

      if (VmSettings.TRACE_SMALL_IDS) {
        storage.put((byte) (PROMISE_MESSAGE | (usedBytes << 4)));
        writeId(usedBytes, senderId);
        writeId(usedBytes, resolverId);
      } else {
        storage.putByteIntInt((byte) (PROMISE_MESSAGE | (3 << 4)), senderId, resolverId);
      }
    }

    public void recordExternalMessage(final int senderId, final short method,
        final int dataId, final TraceActorContext tracer) {
      ensureSufficientSpace(11, tracer);

      if (VmSettings.TRACE_SMALL_IDS) {
        int usedBytes = getUsedBytes(senderId);
        storage.put((byte) (EXTERNAL_BIT | MESSAGE | (usedBytes << 4)));
        writeId(usedBytes, senderId);
      } else {
        storage.putByteInt((byte) (EXTERNAL_BIT | MESSAGE | (3 << 4)), senderId);
      }
      storage.putShortInt(method, senderId);
    }

    public void recordExternalPromiseMessage(final int senderId, final int resolverId,
        final short method, final int dataId, final TraceActorContext tracer) {
      ensureSufficientSpace(15, tracer);

      if (VmSettings.TRACE_SMALL_IDS) {
        int usedBytes = Math.max(getUsedBytes(resolverId), getUsedBytes(senderId));
        storage.put((byte) (EXTERNAL_BIT | PROMISE_MESSAGE | (usedBytes << 4)));

        writeId(usedBytes, senderId);
        writeId(usedBytes, resolverId);
      } else {
        storage.putByteIntInt((byte) (EXTERNAL_BIT | PROMISE_MESSAGE | (3 << 4)), senderId,
            resolverId);
      }

      storage.putShortInt(method, senderId);
    }

    public void recordSystemCall(final int dataId, final TraceActorContext tracer) {
      ensureSufficientSpace(5, tracer);
      storage.putByteInt(SYSTEM_CALL, dataId);
    }

    private void writeId(final int usedBytes, final int id) {
      switch (usedBytes) {
        case 0:
          storage.put((byte) id);
          break;
        case 1:
          storage.putShort((short) id);
          break;
        case 2:
          storage.putByteShort((byte) (id >> 16), (short) id);
          break;
        case 3:
          storage.putInt(id);
          break;
      }
    }
  }
}
