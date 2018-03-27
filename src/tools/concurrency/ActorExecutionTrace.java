package tools.concurrency;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.SPromise.STracingPromise;
import tools.ObjectBuffer;


public class ActorExecutionTrace {
  // events
  public static byte ACTOR_CREATION  = 0;
  public static byte ACTOR_SCOPE     = 1;
  public static byte MESSAGE         = 2;
  public static byte PROMISE_MESSAGE = 3;
  // flags
  public static byte EXTERNAL_BIT = 4;

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

  public static void recordMessages(final EventualMessage m,
      final ObjectBuffer<EventualMessage> moreCurrent) {
    TracingActivityThread t = getThread();
    ((ActorTraceBuffer) t.getBuffer()).recordMessages(m, moreCurrent);
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

    public void recordActorContext(final Actor actor) {
      currentActor = actor;
      int id = actor.getActorId();
      ensureSufficientSpace(6);

      int unusedBytes = Integer.numberOfLeadingZeros(id) / 8;
      storage.put((byte) (ACTOR_SCOPE | (unusedBytes << 3)));

      storage.put(actor.getOrdering());
      switch (unusedBytes) {
        case 0:
          storage.put((byte) id);
          break;
        case 1:
          storage.putChar((char) id);
          break;
        case 2:
          storage.put((byte) (id >> 16));
          storage.putChar((char) id);
          break;
        case 3:
          storage.putInt(id);
          break;
      }
    }

    public void recordActorCreation(final int childId) {
      ensureSufficientSpace(5);

      int unusedBytes = Integer.numberOfLeadingZeros(childId) / 8;
      storage.put((byte) (ACTOR_CREATION | (unusedBytes << 3)));

      switch (unusedBytes) {
        case 0:
          storage.put((byte) childId);
          break;
        case 1:
          storage.putChar((char) childId);
          break;
        case 2:
          storage.put((byte) (childId >> 16));
          storage.putChar((char) childId);
          break;
        case 3:
          storage.putInt(childId);
          break;
      }
    }

    public void recordMessages(final EventualMessage m,
        final ObjectBuffer<EventualMessage> moreCurrent) {

      writeMessage(m);

      if (moreCurrent != null) {
        for (EventualMessage em : moreCurrent) {
          writeMessage(em);
        }
      }
    }

    private void writeMessage(final EventualMessage em) {
      if (em instanceof PromiseMessage) {
        PromiseMessage pm = (PromiseMessage) em;
        recordPromiseMessage(em.getSender().getActorId(),
            ((STracingPromise) pm.getPromise()).getResolvingActor());
      } else {
        recordMessage(em.getSender().getActorId());
      }
    }

    private void recordMessage(final int senderId) {
      ensureSufficientSpace(5);
      int unusedBytes = Integer.numberOfLeadingZeros(senderId) / 8;
      storage.put((byte) (MESSAGE | (unusedBytes << 3)));

      switch (unusedBytes) {
        case 0:
          storage.put((byte) senderId);
          break;
        case 1:
          storage.putChar((char) senderId);
          break;
        case 2:
          storage.put((byte) (senderId >> 16));
          storage.putChar((char) senderId);
          break;
        case 3:
          storage.putInt(senderId);
          break;
      }
    }

    private void recordPromiseMessage(final int senderId, final int resolverId) {
      ensureSufficientSpace(9);
      int unusedBytes = Math.min(Integer.numberOfLeadingZeros(senderId),
          Integer.numberOfLeadingZeros(resolverId)) / 8;

      storage.put((byte) (PROMISE_MESSAGE | (unusedBytes << 3)));

      switch (unusedBytes) {
        case 0:
          storage.put((byte) senderId);
          storage.put((byte) resolverId);
          break;
        case 1:
          storage.putChar((char) senderId);
          storage.putChar((char) resolverId);
          break;
        case 2:
          storage.put((byte) (senderId >> 16));
          storage.putChar((char) senderId);
          storage.put((byte) (resolverId >> 16));
          storage.putChar((char) resolverId);
          break;
        case 3:
          storage.putInt(senderId);
          storage.putInt(resolverId);
          break;
      }
    }
  }
}
