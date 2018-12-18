package tools.snapshot;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.primitives.ObjectPrims.ClassPrim;
import som.vmobjects.SClass;
import tools.concurrency.TracingActors.TracingActor;


public class SnapshotRecord {
  /**
   * This map allows us to know if we already serialized an object (and avoid circles).
   * We can get the location of the serialized object in the trace
   */
  private final EconomicMap<Object, Long> entries;
  protected final TracingActor            owner;
  private int                             msgCnt;

  /**
   * This list is used to keep track of references to unserialized objects in the actor owning
   * this buffer.
   * It serves both the purpose of being a todo-list and remembering to fix these references
   * after they were serialized. The idea is that the owner regularly checks the queue, and for
   * each element serializes the object if necessary. The offset of the object within this
   * SnapshotBuffer is then known and used to fix the reference (writing a long in another
   * buffer at a specified location).
   */
  private final ConcurrentLinkedQueue<FarRefTodo> externalReferences;

  public SnapshotRecord(final TracingActor owner) {
    this.entries = EconomicMap.create();
    this.externalReferences = new ConcurrentLinkedQueue<>();
    this.owner = owner;
    msgCnt = 0;
  }

  public long getMessageIdentifier() {
    long result = (((long) owner.getActorId()) << 32) | msgCnt;
    msgCnt++;
    return result;
  }

  /**
   * only use this in the actor that owns this record (only the owner adds entries).
   */
  @TruffleBoundary
  public boolean containsObjectUnsync(final Object o) {
    return entries.containsKey(o);
  }

  @TruffleBoundary
  public boolean containsObject(final Object o) {
    synchronized (entries) {
      return entries.containsKey(o);
    }
  }

  @TruffleBoundary
  public long getObjectPointer(final Object o) {
    if (entries.containsKey(o)) {
      return entries.get(o);
    }
    throw new IllegalArgumentException(
        "Cannot point to unserialized Objects, you are missing a serialization call: " + o);
  }

  @TruffleBoundary
  public void addObjectEntry(final Object o, final long offset) {
    synchronized (entries) {
      entries.put(o, offset);
    }
  }

  public void handleTodos(final SnapshotBuffer sb, final ClassPrim classPrim) {
    // SnapshotBackend.removeTodo(this);
    while (!externalReferences.isEmpty()) {
      FarRefTodo frt = externalReferences.poll();

      // ignore todos from a different snapshot
      if (frt.referer.snapshotVersion == sb.snapshotVersion) {
        if (!this.containsObjectUnsync(frt.target)) {
          if (frt.target instanceof PromiseMessage) {
            ((PromiseMessage) frt.target).forceSerialize(sb);
          } else {
            SClass clazz = classPrim.executeEvaluated(frt.target);
            clazz.serialize(frt.target, sb);
          }
        }
        frt.resolve(getObjectPointer(frt.target));
      }
    }

  }

  /**
   * This method handles all the details of what to do when we want to serialize objects from
   * another actor.
   * Intended for use in FarReference serialization.
   *
   * @param o object far-referenced from {@code other}}
   * @param other {SnapshotBuffer that contains the farReference}
   * @param destination offset of the reference inside {@code other}
   */
  public void farReference(final Object o, final SnapshotBuffer other,
      final int destination) {
    Long l = getEntrySynced(o);

    if (l != null) {
      other.putLongAt(destination, l);
    } else {
      if (externalReferences.isEmpty()) {
        SnapshotBackend.addUnfinishedTodo(this);
      }
      externalReferences.offer(new FarRefTodo(other, destination, o));
    }
  }

  public void farReferenceMessage(final PromiseMessage pm, final SnapshotBuffer other,
      final int destination) {
    Long l = getEntrySynced(pm);

    if (l != null) {
      other.putLongAt(destination, l);
    } else {
      if (externalReferences.isEmpty()) {
        SnapshotBackend.addUnfinishedTodo(this);
      }
      externalReferences.offer(new FarRefTodo(other, destination, pm));
    }
  }

  @TruffleBoundary
  private Long getEntrySynced(final Object o) {
    Long l;
    synchronized (entries) {
      l = entries.get(o);
    }
    return l;
  }

  public static final class FarRefTodo {
    private final SnapshotBuffer referer;
    private final int            referenceOffset;
    final Object                 target;

    FarRefTodo(final SnapshotBuffer referer, final int referenceOffset,
        final Object target) {
      this.referer = referer;
      this.referenceOffset = referenceOffset;
      this.target = target;
    }

    public void resolve(final long targetOffset) {
      referer.putLongAt(referenceOffset, targetOffset);
    }
  }
}
