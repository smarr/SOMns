package tools.snapshot.deserialization;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.graalvm.collections.EconomicMap;

import som.interpreter.actors.Actor;
import som.vmobjects.SClass;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.deserialization.FixupInformation.FixupList;


public class DeserializationBuffer {

  private final ByteBuffer                wrapped;
  private final EconomicMap<Long, Object> deserialized;
  private long                            lastRef;

  public DeserializationBuffer(final byte[] backing) {
    wrapped = ByteBuffer.wrap(backing).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
    wrapped.rewind();
    deserialized = EconomicMap.create();
  }

  public byte get() {
    return wrapped.get();
  }

  public void get(final byte[] b) {
    wrapped.get(b);
  }

  public short getShort() {
    return wrapped.getShort();
  }

  public int getInt() {
    return wrapped.getInt();
  }

  public long getLong() {
    return wrapped.getLong();
  }

  public double getDouble() {
    return wrapped.getDouble();
  }

  public Object deserialize(final long current) {
    assert !deserialized.containsKey(current);
    this.position((int) current);

    // to avoid endless loop, when null is read we replace it with a linked list containing
    // fixup information
    deserialized.put(current, null);
    short cId = getShort();
    Object o = SnapshotBackend.lookupClass(cId).getSerializer().deserialize(this);

    fixUpIfNecessary(current, o);
    deserialized.put(current, o);
    return o;
  }

  public Object getReference() {
    long reference = getLong();
    lastRef = reference;
    if (!deserialized.containsKey(reference)) {
      int current = position();

      deserialized.put(reference, null);

      // prepare deserialize referenced object
      position((int) reference);
      short classId = getShort();
      SClass clazz = SnapshotBackend.lookupClass(classId);
      Object o = clazz.getSerializer().deserialize(this);

      // continue with current object
      position(current);
      fixUpIfNecessary(reference, o);
      deserialized.put(reference, o);
      return o;
    } else {
      return deserialized.get(reference);
    }
  }

  public static boolean needsFixup(final Object o) {
    return o == null || o instanceof FixupList;
  }

  public synchronized void installFixup(final FixupInformation fi) {
    FixupList fl = (FixupList) deserialized.get(lastRef);
    if (fl == null) {
      deserialized.put(lastRef, new FixupList(fi));
    } else {
      fl.add(fi);
    }
  }

  private synchronized void fixUpIfNecessary(final long reference, final Object result) {
    Object ref = deserialized.get(reference);
    if (ref instanceof FixupList) {
      // we have fixup information, this means that this object is part of a circular
      // relationship
      for (FixupInformation fi : (FixupList) ref) {
        fi.fixUp(result);
      }
    }
  }

  public int position() {
    return wrapped.position();
  }

  public void position(final int newPosition) {
    wrapped.position(newPosition);
  }

  public Actor getActor() {
    return null;
  }
}
