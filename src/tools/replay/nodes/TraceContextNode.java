package tools.replay.nodes;

import com.oracle.truffle.api.dsl.Specialization;

import som.vm.Activity;
import tools.concurrency.TracingActors.TracingActor;
import tools.replay.actors.ActorExecutionTrace;
import tools.replay.actors.ActorExecutionTrace.ActorTraceBuffer;


public abstract class TraceContextNode extends TraceNode {

  public abstract void execute(Activity a);

  @Specialization
  protected void recordActorContext(final TracingActor actor) {
    writeContext(actor.getActorId(), actor.getNextTraceBufferId());
  }

  @Specialization
  protected void recordGeneric(final Activity activity) {
    writeContext(activity.getId(), activity.getNextTraceBufferId());
  }

  protected void writeContext(final long id, final int bufferId) {
    ActorTraceBuffer buffer = getCurrentBuffer();
    int pos = buffer.position();

    buffer.putByteAt(pos, ActorExecutionTrace.ACTOR_CONTEXT);
    buffer.putShortAt(pos + 1, (short) bufferId);
    buffer.putLongAt(pos + 3, id);

    buffer.position(pos + 1 + Short.BYTES + Long.BYTES);
  }
}
