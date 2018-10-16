package tools.snapshot;

import org.graalvm.collections.EconomicMap;

import som.interpreter.objectstorage.ClassFactory;
import som.vm.constants.Classes;
import tools.concurrency.TraceBuffer;
import tools.replay.nodes.TraceActorContextNode;
import tools.snapshot.deserialization.DeserializationBuffer;


public class SnapshotBuffer extends TraceBuffer {

  public static final int FIELD_SIZE    = 8;
  public static final int CLASS_ID_SIZE = 2;
  public static final int MAX_FIELD_CNT = Byte.MAX_VALUE;

  /**
   * This map allows us to know if we already serialized an object (and avoid circles).
   * We can get the location of the serialized object in the trace
   */
  private final EconomicMap<Object, Long> entries;

  public SnapshotBuffer() {
    super(true);
    this.entries = EconomicMap.create();
  }

  public boolean containsObject(final Object o) {
    return entries.containsKey(o);
  }

  public long getObjectPointer(final Object o) {
    if (entries.containsKey(o)) {
      return entries.get(o);
    }
    throw new IllegalArgumentException(
        "Cannot point to unserialized Objects, you are missing a serialization call: " + o);
  }

  public int addObject(final Object o, final ClassFactory classFact, final int payload) {
    assert !entries.containsKey(o) : "Object serialized multiple times";
    entries.put(o, (long) this.position);
    int oldPos = this.position;
    this.putShortAt(this.position,
        classFact.getIdentifier().getSymbolId());
    this.position += CLASS_ID_SIZE + payload;
    return oldPos + CLASS_ID_SIZE;
  }

  public int addObjectWithFields(final Object o, final ClassFactory classFact,
      final int fieldCnt) {
    assert fieldCnt < MAX_FIELD_CNT;
    assert !entries.containsKey(o) : "Object serialized multiple times";

    entries.put(o, (long) this.position);
    int oldPos = this.position;

    this.putShortAt(this.position,
        classFact.getIdentifier().getSymbolId());
    this.position += CLASS_ID_SIZE + (FIELD_SIZE * fieldCnt);
    return oldPos + CLASS_ID_SIZE;
  }

  public int addMessage(final int payload) {
    // we dont put messages into our lookup table as there should be only one reference to it
    // (either from a promise or a mailbox)
    int oldPos = this.position;

    // TODO use Message Class
    this.putShortAt(this.position,
        Classes.messageClass.getFactory().getClassName().getSymbolId());
    this.position += CLASS_ID_SIZE + payload;
    return oldPos + CLASS_ID_SIZE;
  }

  @Override
  protected void swapBufferWhenNotEnoughSpace(final TraceActorContextNode tracer) {
    throw new UnsupportedOperationException("TODO find a solution for snapshot size");
  }

  // for testing purposes
  public DeserializationBuffer getBuffer() {
    return new DeserializationBuffer(buffer);
  }
}
