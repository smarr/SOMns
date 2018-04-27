package tools.concurrency.nodes;

import com.oracle.truffle.api.dsl.Specialization;

import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.ActorExecutionTrace.ActorTraceBuffer;
import tools.concurrency.ByteBuffer;
import tools.concurrency.TracingActors.TracingActor;


public abstract class TraceActorCreationNode extends TraceNode {

  @Specialization(guards = {"smallIds()", "byteId(actor)"})
  public void traceByteId(final TracingActor actor) {
    ByteBuffer storage = getStorage();
    storage.put((byte) (ActorExecutionTrace.ACTOR_CREATION | (0 << 4)));
    storage.put((byte) actor.getActorId());
  }

  @Specialization(guards = {"smallIds()", "shortId(actor)"}, replaces = "traceByteId")
  public void traceShortId(final TracingActor actor) {
    ByteBuffer storage = getStorage();
    storage.putByteShort((byte) (ActorExecutionTrace.ACTOR_CREATION | (1 << 4)),
        (short) actor.getActorId());
  }

  @Specialization(guards = {"smallIds()", "threeByteId(actor)"},
      replaces = {"traceShortId", "traceByteId"})
  public void traceThreeByteId(final TracingActor actor) {
    ByteBuffer storage = getStorage();
    int id = actor.getActorId();
    storage.putByteByteShort((byte) (ActorExecutionTrace.ACTOR_CREATION | (2 << 4)),
        (byte) (id >> 16), (short) id);
  }

  @Specialization(replaces = {"traceShortId", "traceByteId", "traceThreeByteId"})
  public void traceStandardId(final TracingActor actor) {
    ByteBuffer storage = getStorage();
    int id = actor.getActorId();
    storage.putByteInt((byte) (ActorExecutionTrace.ACTOR_CREATION | (3 << 4)), id);
  }

  @Child TraceActorContextNode tracer = TraceActorContextNodeGen.create();

  private ByteBuffer getStorage() {
    ActorTraceBuffer buffer = getCurrentBuffer();
    return buffer.ensureSufficientSpace(5, tracer);
  }
}
