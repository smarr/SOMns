package tools.concurrency.nodes;

import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.ActorExecutionTrace.ActorTraceBuffer;
import tools.concurrency.TracingActors.TracingActor;


public final class TraceActorContextNode extends TraceNode {

  @Child protected RecordIdNode id = RecordIdNodeGen.create();

  public void trace(final TracingActor actor) {
    ActorTraceBuffer buffer = getCurrentBuffer();
    int pos = buffer.position();

    int idLen = id.execute(buffer, pos + 3, actor.getActorId());
    int idBit = (idLen - 1) << 4;

    buffer.putByteAt(pos, (byte) (ActorExecutionTrace.ACTOR_CONTEXT | idBit));
    buffer.putShortAt(pos + 1, actor.getOrdering());

    buffer.position(pos + idLen + 1 + 2);
  }
}
