package tools.concurrency;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.SPromise.STracingPromise;
import tools.ObjectBuffer;


public class ActorExecutionTrace {

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

    private void writeId(final int id) {
      if (id < 0) {
        storage.putInt(id);
      } else if (id <= 0xFF) {
        storage.put((byte) id);
      } else if (id <= 0xFFFF) {
        storage.putChar((char) id);
      } else if (id <= 0xFFFFFF) {
        storage.put((byte) (id >> 16));
        storage.putChar((char) id);
      }
    }

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
      ensureSufficientSpace(5);
      storage.put((byte) 1);
      writeId(actor.getActorId());
      storage.put(actor.getOrdering());
    }

    public void recordActorCreation(final int childId) {
      ensureSufficientSpace(5);
      storage.put((byte) 2);
      writeId(childId);
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
      storage.put((byte) 3);
      writeId(senderId);
    }

    private void recordPromiseMessage(final int senderId, final int resolverId) {
      ensureSufficientSpace(9);
      storage.put((byte) 4);
      writeId(senderId);
      writeId(resolverId);
    }
  }
}
