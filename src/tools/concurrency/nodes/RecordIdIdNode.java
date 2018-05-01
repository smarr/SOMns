package tools.concurrency.nodes;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

import som.vm.VmSettings;
import tools.concurrency.ActorExecutionTrace.ActorTraceBuffer;


public abstract class RecordIdIdNode extends Node {
  private static final int BYTE_LEN       = 1;
  private static final int SHORT_LEN      = 2;
  private static final int THREE_BYTE_LEN = 3;
  private static final int INT_LEN        = 4;

  public abstract int execute(ActorTraceBuffer storage, int idx, int id1, int id2);

  protected static boolean smallIds() {
    return VmSettings.TRACE_SMALL_IDS;
  }

  protected static boolean byteId(final int id1, final int id2) {
    return (id1 & id2 & 0xFFFFFF00) == 0;
  }

  protected static boolean shortId(final int id1, final int id2) {
    return (id1 & id2 & 0xFFFF0000) == 0;
  }

  protected static boolean threeByteId(final int id1, final int id2) {
    return (id1 & id2 & 0xFF000000) == 0;
  }

  @Specialization(guards = {"smallIds()", "byteId(id1, id2)"})
  public int traceByteId(final ActorTraceBuffer storage, final int idx, final int id1,
      final int id2) {
    storage.putByteAt(idx, (byte) id1);
    storage.putByteAt(idx + 1, (byte) id2);
    return BYTE_LEN;
  }

  @Specialization(guards = {"smallIds()", "shortId(id1, id2)"},
      replaces = "traceByteId")
  public int traceShortId(final ActorTraceBuffer storage, final int idx, final int id1,
      final int id2) {
    storage.putShortAt(idx, (short) id1);
    storage.putShortAt(idx + 2, (short) id2);
    return SHORT_LEN;
  }

  @Specialization(guards = {"smallIds()", "threeByteId(id1, id2)"},
      replaces = {"traceShortId", "traceByteId"})
  public int traceThreeByteId(final ActorTraceBuffer storage, final int idx, final int id1,
      final int id2) {
    storage.putByteShortAt(idx, (byte) (id1 >> 16), (short) id1);
    storage.putByteShortAt(idx + 3, (byte) (id2 >> 16), (short) id2);
    return THREE_BYTE_LEN;
  }

  @Specialization(replaces = {"traceShortId", "traceByteId", "traceThreeByteId"})
  public int traceStandardId(final ActorTraceBuffer storage, final int idx, final int id1,
      final int id2) {
    storage.putIntAt(idx, id1);
    storage.putIntAt(idx + 4, id2);
    return INT_LEN;
  }
}
