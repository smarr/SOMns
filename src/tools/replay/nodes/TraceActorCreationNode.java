package tools.replay.nodes;

import som.vm.VmSettings;
import tools.concurrency.TracingActors.TracingActor;
import tools.replay.TraceRecord;
import tools.replay.nodes.RecordEventNodes.RecordOneEvent;


public final class TraceActorCreationNode extends TraceNode {
  @Child protected RecordOneEvent roe =
      new RecordOneEvent(TraceRecord.ACTIVITY_CREATION);

  private TraceActorCreationNode() {}

  public void trace(final TracingActor actor) {
    roe.record(actor.getId());
  }

  public static TraceActorCreationNode create() {
    if (VmSettings.ACTOR_TRACING) {
      return new TraceActorCreationNode();
    } else {
      return null;
    }
  }
}
