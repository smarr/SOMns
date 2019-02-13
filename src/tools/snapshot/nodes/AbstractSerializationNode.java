package tools.snapshot.nodes;

import com.oracle.truffle.api.nodes.Node;

import som.vm.VmSettings;
import som.vmobjects.SClass;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer;


public abstract class AbstractSerializationNode extends Node {

  public AbstractSerializationNode() {
    assert VmSettings.SNAPSHOTS_ENABLED;
  }

  public abstract void execute(Object o, SnapshotBuffer sb);

  protected abstract Object deserialize(DeserializationBuffer bb);

  public Object deserialize(final DeserializationBuffer bb, final SClass clazz) {
    return deserialize(bb);
  }
}
