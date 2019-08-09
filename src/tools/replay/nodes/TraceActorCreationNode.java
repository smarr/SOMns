package tools.replay.nodes;

import tools.concurrency.TracingActors.TracingActor;
import tools.replay.TraceRecord;
import tools.replay.nodes.RecordEventNodes.RecordOneEvent;


public final class TraceActorCreationNode extends TraceNode {
  @Child protected RecordOneEvent roe =
      new RecordOneEvent(TraceRecord.ACTOR_CREATION);

  public void trace(final TracingActor actor) {
    roe.record(actor.getId());
  }
}
