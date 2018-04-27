package tools.concurrency.nodes;

import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.ActorExecutionTrace.ActorTraceBuffer;
import tools.concurrency.ByteBuffer;
import tools.concurrency.TracingActors.TracingActor;


public final class TraceActorContextNode extends TraceNode {

  @Child protected RecordIdNode id = RecordIdNodeGen.create();

  public void trace(final TracingActor actor) {
    ByteBuffer storage = getStorage();
    int pos = storage.position();

    int idLen = id.execute(storage, pos + 3, actor.getActorId());
    int idBit = (idLen - 1) << 4;

    storage.putByteAt(pos, (byte) (ActorExecutionTrace.ACTOR_CONTEXT | idBit));
    storage.putShortAt(pos + 1, actor.getOrdering());

    storage.position(pos + idLen + 1 + 2);
  }

  private static ByteBuffer getStorage() {
    ActorTraceBuffer buffer = getCurrentBuffer();
    return buffer.getStorage();
  }
}
