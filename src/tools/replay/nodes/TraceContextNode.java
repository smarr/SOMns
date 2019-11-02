package tools.replay.nodes;

import com.oracle.truffle.api.dsl.Specialization;

import som.primitives.processes.ChannelPrimitives.Process;
import som.vm.Activity;
import tools.concurrency.TracingActors.TracingActor;
import tools.replay.TraceRecord;
import tools.replay.actors.ActorExecutionTrace.ActorTraceBuffer;


public abstract class TraceContextNode extends TraceNode {

  public abstract void execute(Activity a);

  @Specialization
  protected void recordActorContext(final TracingActor actor) {
    writeContext(actor.getId(), actor.getNextTraceBufferId());
  }

  @Specialization
  protected void recordProcess(final Process process) {
    writeContext(process.getId(), process.getNextTraceBufferId());
  }

  @Specialization
  protected void recordGeneric(final Activity activity) {
    writeContext(activity.getId(), activity.getNextTraceBufferId());
  }

  protected void writeContext(final long id, final int bufferId) {
    ActorTraceBuffer buffer = getCurrentBuffer();
    int pos = buffer.position();

    buffer.putByteAt(pos, TraceRecord.ACTIVITY_CONTEXT.value);
    buffer.putShortAt(pos + 1, (short) bufferId);
    buffer.putLongAt(pos + 3, id);

    buffer.position(pos + 1 + Short.BYTES + Long.BYTES);
  }
}
