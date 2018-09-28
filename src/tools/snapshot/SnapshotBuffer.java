package tools.snapshot;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.graalvm.collections.EconomicMap;

import som.vm.Symbols;
import som.vmobjects.SClass;
import tools.concurrency.TraceBuffer;
import tools.replay.nodes.TraceActorContextNode;


public class SnapshotBuffer extends TraceBuffer {

  public static final int FIELD_SIZE    = 8;
  public static final int CLASS_ID_SIZE = 2;
  public static final int MAX_FIELD_CNT = Byte.MAX_VALUE;

  public SnapshotBuffer() {
    super(true);
    this.entries = EconomicMap.create();
  }

  // This map allows us to know if we already serialized an object (and avoid circles)
  // We can get the location of the serialized object in the trace
  private EconomicMap<Object, Long> entries;

  public boolean containsObject(final Object o) {
    return entries.containsKey(o);
  }

  public long getObjectPointer(final Object o) {
    if (entries.containsKey(o)) {
      return entries.get(o);
    }
    return -1;
  }

  public int addObject(final Object o, final SClass cls, final int payload) {
    entries.put(o, (long) this.position);
    int oldPos = this.position;
    this.putShortAt(this.position,
        Symbols.symbolFor(cls.getMixinDefinition().getIdentifier()).getSymbolId());
    this.position += CLASS_ID_SIZE + payload;
    return oldPos + CLASS_ID_SIZE;
  }

  public int addObjectWithFields(final Object o, final SClass cls, final int fieldCnt) {
    assert fieldCnt < MAX_FIELD_CNT;
    entries.put(o, (long) this.position);
    int oldPos = this.position;

    this.putShortAt(this.position,
        Symbols.symbolFor(cls.getMixinDefinition().getIdentifier()).getSymbolId());
    this.position += CLASS_ID_SIZE + (FIELD_SIZE * fieldCnt);
    return oldPos + CLASS_ID_SIZE;
  }

  @Override
  protected void swapBufferWhenNotEnoughSpace(final TraceActorContextNode tracer) {
    throw new UnsupportedOperationException();
    // todo find a solution for snapshot size
  }

  // for testing purposes
  public ByteBuffer getBuffer() {
    ByteBuffer bb =
        ByteBuffer.wrap(this.buffer).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
    bb.rewind();
    return bb;
  }
}
