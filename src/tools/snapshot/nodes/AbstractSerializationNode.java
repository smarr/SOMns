package tools.snapshot.nodes;

import java.nio.ByteBuffer;

import com.oracle.truffle.api.nodes.Node;

import som.vmobjects.SClass;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.SnapshotBuffer;


public abstract class AbstractSerializationNode extends Node {

  public abstract void serialize(Object o, SnapshotBuffer sb);

  public abstract Object deserialize(ByteBuffer sb);

  public static Object deserializeReference(final ByteBuffer bb) {
    long reference = bb.getLong();
    int current = bb.position();

    // prepare deserialize referenced object
    bb.position((int) reference);
    short classId = bb.getShort();
    SClass clazz = SnapshotBackend.lookupClass(classId);
    Object o = clazz.getSerializer().deserialize(bb);

    // continue with current object
    bb.position(current);
    return o;
  }
}
