package tools.snapshot.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import som.interpreter.SomLanguage;
import tools.snapshot.SnapshotBuffer;


public final class SerializerRootNode extends RootNode {
  @Child protected AbstractSerializationNode serializer;

  public SerializerRootNode(final SomLanguage language,
      final AbstractSerializationNode serializer) {
    super(language);
    this.serializer = serializer;
  }

  @Override
  public Object execute(final VirtualFrame frame) {
    serializer.serialize(frame.getArguments()[0], (SnapshotBuffer) frame.getArguments()[1]);
    return null;
  }

  public AbstractSerializationNode getSerializer() {
    return serializer;
  }
}
