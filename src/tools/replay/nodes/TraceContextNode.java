package tools.replay.nodes;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ValueProfile;

import som.vm.Activity;
import tools.replay.TraceRecord;
import tools.replay.actors.UniformExecutionTrace.UniformTraceBuffer;


public abstract class TraceContextNode extends TraceNode {
  private final ValueProfile vp = ValueProfile.createClassProfile();

  public abstract void execute(Activity a);

  @Specialization
  protected void recordGeneric(final Activity activity) {
    writeContext(vp.profile(activity).getId(), vp.profile(activity).getNextTraceBufferId());
  }

  protected void writeContext(final long id, final int bufferId) {
    UniformTraceBuffer buffer = getCurrentBuffer();
    int pos = buffer.position();

    buffer.putByteAt(pos, TraceRecord.ACTIVITY_CONTEXT.value);
    buffer.putShortAt(pos + 1, (short) bufferId);
    buffer.putLongAt(pos + 3, id);

    buffer.position(pos + 1 + Short.BYTES + Long.BYTES);
  }
}
