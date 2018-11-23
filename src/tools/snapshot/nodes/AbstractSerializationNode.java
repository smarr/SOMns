package tools.snapshot.nodes;

import com.oracle.truffle.api.nodes.Node;

import som.interpreter.objectstorage.ClassFactory;
import som.vm.VmSettings;
import som.vmobjects.SClass;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer;


public abstract class AbstractSerializationNode extends Node {
  public final SClass       clazz;
  public final ClassFactory classFact;

  public AbstractSerializationNode(final SClass clazz) {
    assert VmSettings.SNAPSHOTS_ENABLED;
    this.clazz = clazz;
    this.classFact = clazz.getInstanceFactory();
  }

  /**
   * Constructor for CachedSerializationNode.
   */
  public AbstractSerializationNode() {
    assert VmSettings.SNAPSHOTS_ENABLED;
    this.clazz = null;
    this.classFact = null;
  }

  public abstract void execute(Object o, SnapshotBuffer sb);

  public abstract Object deserialize(DeserializationBuffer bb);
}
