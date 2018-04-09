package tools.concurrency;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.SPromise.STracingPromise;


public class ActorExecutionTrace {
  // events
  public static byte ACTOR_CREATION  = 0;
  public static byte ACTOR_CONTEXT   = 1;
  public static byte MESSAGE         = 2;
  public static byte PROMISE_MESSAGE = 3;
  public static byte SYSTEM_CALL     = 4;
  // flags
  public static byte EXTERNAL_BIT = 8;

  private static TracingActivityThread getThread() {
    Thread current = Thread.currentThread();
    assert current instanceof TracingActivityThread;
    return (TracingActivityThread) current;
  }

  public static void recordActorContext(final Actor actor) {
    TracingActivityThread t = getThread();
    ((ActorTraceBuffer) t.getBuffer()).recordActorContext(actor);
  }

  public static void recordActorCreation(final int childId) {
    TracingActivityThread t = getThread();
    ((ActorTraceBuffer) t.getBuffer()).recordActorCreation(childId);
  }

  public static void recordMessage(final EventualMessage msg) {
    TracingActivityThread t = getThread();
    if (msg instanceof PromiseMessage) {
      ((ActorTraceBuffer) t.getBuffer()).recordPromiseMessage(msg.getSender().getActorId(),
          ((STracingPromise) ((PromiseMessage) msg).getPromise()).getResolvingActor());
    } else {
      ((ActorTraceBuffer) t.getBuffer()).recordMessage(msg.getSender().getActorId());
    }
  }

  public static void actorFinished() {
    TracingActivityThread t = getThread();
    t.getBuffer().swapStorage();
  }

  public static class ActorTraceBuffer extends TraceBuffer {
    Actor currentActor;

    @TruffleBoundary
    @Override
    protected boolean ensureSufficientSpace(final int requiredSpace) {
      if (storage.remaining() < requiredSpace) {
        boolean didSwap = swapStorage();
        assert didSwap;
        recordActorContext(currentActor);
        return didSwap;
      }
      return false;
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

    public void recordActorContext(final Actor actor) {
      currentActor = actor;
      int id = actor.getActorId();
      ensureSufficientSpace(7);

      int usedBytes = getUsedBytes(id);
      storage.put((byte) (ACTOR_CONTEXT | (usedBytes << 4)));

      storage.putShort(actor.getOrdering());
      writeId(usedBytes, id);
    }

    public void recordActorCreation(final int childId) {
      ensureSufficientSpace(5);
      int usedBytes = getUsedBytes(childId);
      storage.put((byte) (ACTOR_CREATION | (usedBytes << 4)));
      writeId(usedBytes, childId);
    }

    public void recordMessage(final int senderId) {
      ensureSufficientSpace(5);
      int usedBytes = getUsedBytes(senderId);
      storage.put((byte) (MESSAGE | (usedBytes << 4)));
      writeId(usedBytes, senderId);
    }

    public void recordPromiseMessage(final int senderId, final int resolverId) {
      ensureSufficientSpace(9);
      int usedBytes = Math.max(getUsedBytes(resolverId), getUsedBytes(senderId));

      storage.put((byte) (PROMISE_MESSAGE | (usedBytes << 4)));

      writeId(usedBytes, senderId);
      writeId(usedBytes, resolverId);
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
          storage.put((byte) (id >> 16));
          storage.putShort((short) id);
          break;
        case 3:
          storage.putInt(id);
          break;
      }
    }
  }
}
