package tools.snapshot.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import som.interpreter.SomLanguage;


public final class SerializerRootNode extends RootNode {
  @Child protected AbstractSerializationNode serializer;

  public SerializerRootNode(final SomLanguage language,
      final AbstractSerializationNode serializer) {
    super(language);
    this.serializer = insert(serializer);
  }

  @Override
  public Object execute(final VirtualFrame frame) {
    throw new UnsupportedOperationException(
        "Don't use this execute method, instead directly use the serializer");
  }

  public AbstractSerializationNode getSerializer() {
    return serializer;
  }
}
