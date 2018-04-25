package tools.concurrency.nodes;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

import som.vm.VmSettings;
import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.ActorExecutionTrace.ActorTraceBuffer;
import tools.concurrency.ByteBuffer;
import tools.concurrency.TracingActivityThread;
import tools.concurrency.TracingActors.TracingActor;


public abstract class TraceActorContext extends Node {
  public abstract void execute(TracingActor actor);

  protected static boolean smallIds() {
    return VmSettings.TRACE_SMALL_IDS;
  }

  protected static boolean byteId(final TracingActor actor) {
    return (actor.getActorId() & 0xFFFFFF00) == 0;
  }

  protected static boolean shortId(final TracingActor actor) {
    return (actor.getActorId() & 0xFFFF0000) == 0;
  }

  protected static boolean threeByteId(final TracingActor actor) {
    return (actor.getActorId() & 0xFF000000) == 0;
  }

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

  private static TracingActivityThread getThread() {
    Thread current = Thread.currentThread();
    assert current instanceof TracingActivityThread;
    return (TracingActivityThread) current;
  }

  private static ActorTraceBuffer getCurrentBuffer() {
    TracingActivityThread t = getThread();
    return (ActorTraceBuffer) t.getBuffer();
  }
}
