package tools.snapshot.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;

import som.vm.VmSettings;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer;


public abstract class AbstractSerializationNode extends Node {

  public AbstractSerializationNode() {
    assert VmSettings.SNAPSHOTS_ENABLED;
  }

  public abstract long execute(Object o, SnapshotBuffer sb);

  protected abstract Object deserialize(DeserializationBuffer bb);

  public Object deserialize(final DeserializationBuffer bb, final SClass clazz) {
    return deserialize(bb);
  }

  protected static SnapshotBuffer getBuffer() {
    return SnapshotBackend.getValueBuffer();
  }

  @TruffleBoundary
  protected static long getValueLocation(final Object obj) {
    synchronized (SnapshotBackend.getValuepool()) {
      return SnapshotBackend.getValuepool().getOrDefault(obj, (long) -1);
    }
  }

  protected static long getObjectLocation(final SAbstractObject obj, final long current) {
    if (obj.getSnapshotVersion() != current) {
      return -1;
    }

    return obj.getSnapshotLocation();
  }
}
