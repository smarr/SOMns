package tools.snapshot;

import com.oracle.truffle.api.CompilerDirectives;

import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.actors.EventualMessage;
import som.vm.VmSettings;
import som.vm.constants.Classes;
import som.vmobjects.SClass;
import tools.concurrency.TraceBuffer;
import tools.concurrency.TracingActors.TracingActor;
import tools.replay.nodes.TraceActorContextNode;
import tools.snapshot.deserialization.DeserializationBuffer;


public class SnapshotBuffer extends TraceBuffer {

  public static final int FIELD_SIZE    = 8;
  public static final int CLASS_ID_SIZE = 4;
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
    return CompilerDirectives.castExact(owner.getCurrentActor(), TracingActor.class)
                             .getSnapshotRecord();
  }

  public ActorProcessingThread getOwner() {
    return owner;
  }

  public final long calculateReference(final long start) {
    return (owner.getThreadId() << THREAD_SHIFT) | start;
  }

  public int addObject(final Object o, final SClass clazz, final int payload) {
    assert !getRecord().containsObjectUnsync(o) : "Object serialized multiple times";

    int oldPos = this.position;
    getRecord().addObjectEntry(o, calculateReference(oldPos));

    this.putIntAt(this.position, clazz.getIdentity());
    this.position += CLASS_ID_SIZE + payload;
    return oldPos + CLASS_ID_SIZE;
  }

  public int addObjectWithFields(final Object o, final SClass clazz,
      final int fieldCnt) {
    assert fieldCnt < MAX_FIELD_CNT;
    assert !getRecord().containsObjectUnsync(o) : "Object serialized multiple times";

    int oldPos = this.position;
    getRecord().addObjectEntry(o, calculateReference(oldPos));

    this.putIntAt(this.position, clazz.getIdentity());
    this.position += CLASS_ID_SIZE + (FIELD_SIZE * fieldCnt);
    return oldPos + CLASS_ID_SIZE;
  }

  public int addMessage(final int payload, final EventualMessage msg) {
    // we dont put messages into our lookup table as there should be only one reference to it
    // (either from a promise or a mailbox)
    int oldPos = this.position;
    TracingActor ta = (TracingActor) owner.getCurrentActor();
    getRecord().addObjectEntry(msg, calculateReference(oldPos));
    // owner.addMessageLocation(ta.getActorId(), calculateReference(oldPos));

    this.putIntAt(this.position, Classes.messageClass.getIdentity());
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
