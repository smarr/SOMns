package tools.concurrency.nodes;

import com.oracle.truffle.api.dsl.Specialization;

import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.ActorExecutionTrace.ActorTraceBuffer;
import tools.concurrency.ByteBuffer;
import tools.concurrency.TracingActors.TracingActor;


public abstract class TraceActorContext extends TraceNode {

  @Specialization(guards = {"smallIds()", "byteId(actor)"})
  public static void traceByteId(final TracingActor actor) {
    ByteBuffer storage = getStorage();
    storage.putByteShortByte((byte) (ActorExecutionTrace.ACTOR_CREATION | (0 << 4)),
        actor.getOrdering(), (byte) actor.getActorId());
  }

  @Specialization(guards = {"smallIds()", "shortId(actor)"}, replaces = "traceByteId")
  public static void traceShortId(final TracingActor actor) {
    ByteBuffer storage = getStorage();
    storage.putByteShortShort((byte) (ActorExecutionTrace.ACTOR_CREATION | (1 << 4)),
        actor.getOrdering(), (short) actor.getActorId());
  }

  @Specialization(guards = {"smallIds()", "threeByteId(actor)"},
      replaces = {"traceShortId", "traceByteId"})
  public static void traceThreeByteId(final TracingActor actor) {
    ByteBuffer storage = getStorage();
    int id = actor.getActorId();
    storage.putByteShortByteShort((byte) (ActorExecutionTrace.ACTOR_CREATION | (2 << 4)),
        actor.getOrdering(), (byte) (id >> 16), (short) id);
  }

  @Specialization(replaces = {"traceShortId", "traceByteId", "traceThreeByteId"})
  public static void traceStandardId(final TracingActor actor) {
    ByteBuffer storage = getStorage();
    int id = actor.getActorId();
    storage.putByteShortInt((byte) (ActorExecutionTrace.ACTOR_CREATION | (3 << 4)),
        actor.getOrdering(), id);
  }

  private static ByteBuffer getStorage() {
    ActorTraceBuffer buffer = getCurrentBuffer();
    return buffer.getStorage();
  }
}
