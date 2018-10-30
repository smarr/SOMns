package tools.snapshot.nodes;

import com.oracle.truffle.api.nodes.Node;

import som.interpreter.objectstorage.ClassFactory;
import som.vm.VmSettings;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer;


public abstract class AbstractSerializationNode extends Node {
  public final ClassFactory classFact;

  public AbstractSerializationNode(final ClassFactory classFact) {
    assert VmSettings.SNAPSHOTS_ENABLED;
    this.classFact = classFact;
  }

  public abstract void execute(Object o, SnapshotBuffer sb);

  public abstract Object deserialize(DeserializationBuffer bb);
}
