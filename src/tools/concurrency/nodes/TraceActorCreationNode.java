package tools.concurrency.nodes;

import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.ActorExecutionTrace.ActorTraceBuffer;
import tools.concurrency.ByteBuffer;
import tools.concurrency.TracingActors.TracingActor;


public final class TraceActorCreationNode extends TraceNode {

  private static final int TRACE_ENTRY_SIZE = 5;

  @Child protected TraceActorContextNode tracer = new TraceActorContextNode();
  @Child protected RecordIdNode          id     = RecordIdNodeGen.create();

  private ByteBuffer getStorage() {
    ActorTraceBuffer buffer = getCurrentBuffer();
    return buffer.ensureSufficientSpace(TRACE_ENTRY_SIZE, tracer);
  }

  public void trace(final TracingActor actor) {
    ByteBuffer storage = getStorage();
    int pos = storage.position();

    int idLen = id.execute(storage, pos + 1, actor.getActorId());
    int idBit = (idLen - 1) << 4;

    storage.putByteAt(pos, (byte) (ActorExecutionTrace.ACTOR_CREATION | idBit));
    storage.position(pos + idLen + 1);
  }
}
