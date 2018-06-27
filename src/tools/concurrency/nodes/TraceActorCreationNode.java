package tools.concurrency.nodes;

import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.ActorExecutionTrace.ActorTraceBuffer;
import tools.concurrency.TracingActors.TracingActor;


public final class TraceActorCreationNode extends TraceNode {

  private static final int TRACE_ENTRY_SIZE = 5;

  @Child protected TraceActorContextNode tracer = new TraceActorContextNode();
  @Child protected RecordIdNode          id     = RecordIdNodeGen.create();

  public void trace(final TracingActor actor) {
    ActorTraceBuffer buffer = getCurrentBuffer();
    buffer.ensureSufficientSpace(TRACE_ENTRY_SIZE, tracer);
    int pos = buffer.position();

    int idLen = id.execute(buffer, pos + 1, actor.getActorId());
    int idBit = (idLen - 1) << 4;

    buffer.putByteAt(pos, (byte) (ActorExecutionTrace.ACTOR_CREATION | idBit));
    buffer.position(pos + idLen + 1);
  }
}
