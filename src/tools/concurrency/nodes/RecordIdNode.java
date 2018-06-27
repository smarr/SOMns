package tools.concurrency.nodes;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

import som.vm.VmSettings;
import tools.concurrency.ActorExecutionTrace.ActorTraceBuffer;


public abstract class RecordIdNode extends Node {
  private static final int BYTE_LEN       = 1;
  private static final int SHORT_LEN      = 2;
  private static final int THREE_BYTE_LEN = 3;
  private static final int INT_LEN        = 4;

  public abstract int execute(ActorTraceBuffer buffer, int idx, int id);

  protected static boolean smallIds() {
    return VmSettings.TRACE_SMALL_IDS;
  }

  protected static boolean byteId(final int id) {
    return (id & 0xFFFFFF00) == 0;
  }

  protected static boolean shortId(final int id) {
    return (id & 0xFFFF0000) == 0;
  }

  protected static boolean threeByteId(final int id) {
    return (id & 0xFF000000) == 0;
  }

  @Specialization(guards = {"smallIds()", "byteId(id)"})
  public int traceByteId(final ActorTraceBuffer buffer, final int idx, final int id) {
    buffer.putByteAt(idx, (byte) id);
    return BYTE_LEN;
  }

  @Specialization(guards = {"smallIds()", "shortId(id)"},
      replaces = "traceByteId")
  public int traceShortId(final ActorTraceBuffer buffer, final int idx, final int id) {
    buffer.putShortAt(idx, (short) id);
    return SHORT_LEN;
  }

  @Specialization(guards = {"smallIds()", "threeByteId(id)"},
      replaces = {"traceShortId", "traceByteId"})
  public int traceThreeByteId(final ActorTraceBuffer buffer, final int idx, final int id) {
    buffer.putByteShortAt(idx, (byte) (id >> 16), (short) id);
    return THREE_BYTE_LEN;
  }

  @Specialization(replaces = {"traceShortId", "traceByteId", "traceThreeByteId"})
  public int traceStandardId(final ActorTraceBuffer buffer, final int idx, final int id) {
    buffer.putIntAt(idx, id);
    return INT_LEN;
  }
}
