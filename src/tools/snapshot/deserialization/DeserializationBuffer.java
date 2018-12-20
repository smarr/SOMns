package tools.snapshot.deserialization;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

import org.graalvm.collections.EconomicMap;

import som.interpreter.nodes.dispatch.CachedSlotWrite;
import som.vm.VmSettings;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.deserialization.FixupInformation.FixupList;
import tools.snapshot.nodes.ObjectSerializationNodes.SObjectSerializationNode.SlotFixup;


public class DeserializationBuffer {

  protected final ByteBuffer              buffer;
  private final EconomicMap<Long, Object> deserialized;
  private long                            lastRef;
  int                                     depth = 0;
  private ArrayList<Long>                 unserialized;

  public DeserializationBuffer(final byte[] backing) {
    buffer = ByteBuffer.wrap(backing).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
    buffer.rewind();
    deserialized = EconomicMap.create();
    unserialized = new ArrayList<>();
  }

  public DeserializationBuffer(final ByteBuffer buffer) {
    this.buffer = buffer;
    deserialized = EconomicMap.create();
    unserialized = new ArrayList<>();
  }

  public byte get() {
    return buffer.get();
  }

  public void get(final byte[] b) {
    buffer.get(b);
  }

  public short getShort() {
    return buffer.getShort();
  }

  public int getInt() {
    return buffer.getInt();
  }

  public long getLong() {
    return buffer.getLong();
  }

  public double getDouble() {
    return buffer.getDouble();
  }

  public boolean allreadyDeserialized(final long reference) {
    return deserialized.containsKey(reference);
  }

  public int getNumObjects() {
    return deserialized.size();
  }

  private void printPosition(final long current) {
    // Output.print(depth + " - " + getAbsolute(current) + " in " + (current >> 48) + " ");
  }

  public static long getAbsolute(final long current) {
    long pos = (int) current;
    if (!VmSettings.TEST_SNAPSHOTS) {
      pos += SnapshotParser.getFileOffset(current);
    }
    return pos;
  }

  private void printClass(final int cId) {
    // SSymbol sym = SnapshotBackend.getSymbolForId((short) (cId >> 16));
    // Output.println(
    // " " + sym.getString() + ": "
    // + ((short) cId));
  }

  public Object deserialize(final long current) {
    long backup = lastRef;
    long previous = this.position();
    Object result = deserializeWithoutContext(current);
    this.position(previous);
    lastRef = backup;
    return result;
  }

  public Object deserializeWithoutContext(final long current) {
    lastRef = current;
    printPosition(current);
    this.position(current);

    // to avoid endless loop, when null is read we replace it with a linked list containing
    // fixup information

    if (!deserialized.containsKey(current)) {
      deserialized.put(current, null);
    }

    int cId = getInt();
    printClass(cId);

    depth++;
    SClass clazz = SnapshotBackend.lookupClass(cId);
    if (clazz == null) {
      unserialized.add(current);
      depth--;
      return null;
    }

    Object o = clazz.getSerializer().deserialize(this);

    depth--;
    if (o != null) {
      fixUpIfNecessary(current, o);
      deserialized.put(current, o);
    }
    return o;
  }

  public Object getReference() {
    long reference = getLong();

    lastRef = reference;
    if (!deserialized.containsKey(reference)) {
      long current = position();
      printPosition(reference);

      deserialized.put(reference, null);

      // prepare deserialize referenced object
      position(reference);
      int cId = getInt();
      printClass(cId);

      depth++;
      SClass clazz = SnapshotBackend.lookupClass(cId);
      if (clazz == null) {
        unserialized.add(reference);
        depth--;
        position(current);
        return null;
      }

      Object o = clazz.getSerializer().deserialize(this);
      depth--;
      // continue with current object
      position(current);
      fixUpIfNecessary(reference, o);
      deserialized.put(reference, o);
      return o;
    } else {
      printPosition(reference);
      return deserialized.get(reference);
    }
  }

  public Object getMaterializedFrame(final SInvokable invokable) {
    long reference = getLong();

    lastRef = reference;
    if (!deserialized.containsKey(reference)) {
      long current = position();
      deserialized.put(reference, null);

      // prepare deserialize referenced object
      position(reference);
      // need to read the class ID even though it's actually unused
      getInt();

      depth++;
      Object o = invokable.getFrameSerializer().deserialize(this);
      depth--;
      // continue with current object
      position(current);
      fixUpIfNecessary(reference, o);
      deserialized.put(reference, o);
      return o;
    } else {
      return deserialized.get(reference);
    }
  }

  public Object getReference(final long location) {
    return deserialized.get(location);
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

  public synchronized void fixUpIfNecessary(final long reference, final Object result) {
    assert result != null;
    Object ref = deserialized.get(reference);
    if (ref instanceof FixupList) {
      // we have fixup information, this means that this object is part of a circular
      // relationship
      for (FixupInformation fi : (FixupList) ref) {
        fi.fixUp(result);
      }
    }
  }

  public void doUnserialized() {
    while (!unserialized.isEmpty()) {
      ArrayList<Long> todo = unserialized;
      unserialized = new ArrayList<>();

      for (long ref : todo) {
        deserializeWithoutContext(ref);
      }
    }
  }

  public synchronized void putObject(final SObject o) {
    fixUpIfNecessary(lastRef, o);
    deserialized.put(lastRef, o);
  }

  public synchronized void installObjectFixup(final SObject o, final CachedSlotWrite write) {
    long backup = lastRef;
    long reference = getLong();
    long current = position();
    lastRef = reference;
    if (deserialized.containsKey(reference)) {
      Object oo = deserialized.get(reference);
      if (needsFixup(oo)) {
        installFixup(new SlotFixup(o, write));
      } else {
        write.doWrite(o, deserialized.get(reference));
      }
    } else {
      installFixup(new SlotFixup(o, write));
      deserialize(reference);
    }
    lastRef = backup;
    position(current);
  }

  protected void ensureRemaining(final int bytes)
      throws IOException {
    assert buffer.remaining() >= bytes;
  }

  public long position() {
    return buffer.position();
  }

  public void position(final long newPosition) {
    buffer.position((int) newPosition);
  }

  public static class FileDeserializationBuffer extends DeserializationBuffer {
    FileChannel channel;
    // position inside a snapshotbuffer, where the current buffer contents start
    long snapshotPosition;

    public FileDeserializationBuffer(final FileChannel channel) {
      super(ByteBuffer.allocate(VmSettings.BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN));
      buffer.limit(0);
      this.channel = channel;
    }

    @Override
    protected void ensureRemaining(final int bytes) {
      if (buffer.remaining() < bytes) {
        // need to refill buffer
        snapshotPosition += buffer.position();
        buffer.compact();
        try {
          channel.read(buffer);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        buffer.flip();
      }
    }

    @Override
    public long position() {
      return snapshotPosition + buffer.position();
    }

    @Override
    public void position(final long newPosition) {
      snapshotPosition = newPosition;

      // cut away the thread identification
      // 0x FF FF FF FF FF FF
      long offset = 0x0000FFFFFFFFFFFFL & newPosition;

      // absolute position in file
      if (!VmSettings.TEST_SNAPSHOTS) {
        offset += SnapshotParser.getFileOffset(newPosition);
      }

      try {
        assert offset <= channel.size() : "Reading beyond EOF";
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      try {
        channel.position(offset);
        buffer.clear();
        channel.read(buffer);
        buffer.flip();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
