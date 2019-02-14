package tools.snapshot;

import com.oracle.truffle.api.CompilerDirectives;

import som.interpreter.SomLanguage;
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

  public TracingActor getRecord() {
    return CompilerDirectives.castExact(owner.getCurrentActor(), TracingActor.class);
  }

  public ActorProcessingThread getOwner() {
    return owner;
  }

  public final long calculateReference(final long start) {
    return (owner.getThreadId() << THREAD_SHIFT) | start;
  }

  public final long calculateReferenceB(final long start) {
    return (owner.getThreadId() << THREAD_SHIFT) | (start - Integer.BYTES);
  }

  public int reserveSpace(final int bytes) {
    int oldPos = this.position;
    this.position += bytes;
    return oldPos;
  }

  public int addObject(final Object o, final SClass clazz, final int payload) {
    assert !clazz.isSerializedUnsync(o, snapshotVersion) : "Object serialized multiple times";

    int oldPos = this.position;
    clazz.registerLocation(o, calculateReference(oldPos));

    if (clazz.getSOMClass() == Classes.classClass) {
      TracingActor owner = clazz.getOwnerOfOuter();
      if (owner == null) {
        owner = (TracingActor) SomLanguage.getCurrent().getVM().getMainActor();
      }

      assert owner != null;
      owner.farReference(clazz, null, 0);
    }
    this.putIntAt(this.position, clazz.getIdentity());
    this.position += CLASS_ID_SIZE + payload;
    return oldPos + CLASS_ID_SIZE;
  }

  public int addMessage(final int payload, final EventualMessage msg) {
    // we dont put messages into our lookup table as there should be only one reference to it
    // (either from a promise or a mailbox)
    int oldPos = this.position;
    assert !Classes.messageClass.isSerializedUnsync(msg,
        snapshotVersion) : "Message serialized twice, and on the same actor";
    Classes.messageClass.registerLocation(msg, calculateReference(oldPos));
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
