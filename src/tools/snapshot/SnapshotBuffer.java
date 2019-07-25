package tools.snapshot;

import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.objectstorage.ClassFactory;
import som.vm.VmSettings;
import som.vm.constants.Classes;
import tools.concurrency.TraceBuffer;
import tools.concurrency.TracingActors.TracingActor;
import tools.replay.nodes.TraceContextNode;
import tools.snapshot.deserialization.DeserializationBuffer;


public class SnapshotBuffer extends TraceBuffer {

  public static final int FIELD_SIZE    = 8;
  public static final int CLASS_ID_SIZE = 2;
  public static final int MAX_FIELD_CNT = Byte.MAX_VALUE;
  public static final int THREAD_SHIFT  = Long.SIZE - Short.SIZE;

  protected final byte                  snapshotVersion;
  protected final ActorProcessingThread owner;

  public SnapshotBuffer(final ActorProcessingThread owner) {
    super(VmSettings.BUFFER_SIZE * 25);
    this.owner = owner;
    this.snapshotVersion = owner.getSnapshotId();
  }

  public SnapshotRecord getRecord() {
    return ((TracingActor) owner.getCurrentActor()).getSnapshotRecord();
  }

  public ActorProcessingThread getOwner() {
    return owner;
  }

  public final long calculateReference(final long start) {
    return (owner.getThreadId() << THREAD_SHIFT) | start;
  }

  public int addObject(final Object o, final ClassFactory classFact, final int payload) {
    assert !getRecord().containsObject(o) : "Object serialized multiple times";

    int oldPos = this.position;
    getRecord().addObjectEntry(o, calculateReference(oldPos));

    this.putShortAt(this.position,
        classFact.getIdentifier().getSymbolId());
    this.position += CLASS_ID_SIZE + payload;
    return oldPos + CLASS_ID_SIZE;
  }

  public int addObjectWithFields(final Object o, final ClassFactory classFact,
      final int fieldCnt) {
    assert fieldCnt < MAX_FIELD_CNT;
    assert !getRecord().containsObject(o) : "Object serialized multiple times";

    int oldPos = this.position;
    getRecord().addObjectEntry(o, calculateReference(oldPos));

    this.putShortAt(this.position,
        classFact.getIdentifier().getSymbolId());
    this.position += CLASS_ID_SIZE + (FIELD_SIZE * fieldCnt);
    return oldPos + CLASS_ID_SIZE;
  }

  public int addMessage(final int payload) {
    // we dont put messages into our lookup table as there should be only one reference to it
    // (either from a promise or a mailbox)
    int oldPos = this.position;
    getRecord().addMessageEntry(calculateReference(oldPos));

    this.putShortAt(this.position,
        Classes.messageClass.getFactory().getClassName().getSymbolId());
    this.position += CLASS_ID_SIZE + payload;
    return oldPos + CLASS_ID_SIZE;
  }

  @Override
  protected void swapBufferWhenNotEnoughSpace(final TraceContextNode tracer) {
    throw new UnsupportedOperationException("TODO find a solution for snapshot size");
  }

  // for testing purposes
  public DeserializationBuffer getBuffer() {
    return new DeserializationBuffer(buffer);
  }

  public byte[] getRawBuffer() {
    return this.buffer;
  }

  public Byte getSnapshotVersion() {
    return snapshotVersion;
  }

  public boolean needsToBeSnapshot(final long messageId) {
    return VmSettings.TEST_SNAPSHOTS || VmSettings.TEST_SERIALIZE_ALL
        || snapshotVersion > messageId;
  }
}
