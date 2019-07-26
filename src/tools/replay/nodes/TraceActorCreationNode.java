package tools.replay.nodes;

import tools.concurrency.RecordEventNodes.RecordOneEvent;
import tools.concurrency.TracingActors.TracingActor;
import tools.replay.actors.ActorExecutionTrace;


public final class TraceActorCreationNode extends TraceNode {
  @Child protected RecordOneEvent roe =
      new RecordOneEvent(ActorExecutionTrace.ACTOR_CREATION);

  public void trace(final TracingActor actor) {
    roe.record(actor.getId());
  }
}
