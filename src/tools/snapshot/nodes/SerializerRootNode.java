package tools.snapshot.nodes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import som.interpreter.SomLanguage;


public final class SerializerRootNode extends RootNode {
  @CompilationFinal private static SomLanguage lang;

  @Child protected AbstractSerializationNode serializer;

  public static void initializeSerialization(final SomLanguage lang) {
    if (lang != null && SerializerRootNode.lang == null) {
      SerializerRootNode.lang = lang;
    }
  }

  public SerializerRootNode(final AbstractSerializationNode serializer) {
    super(lang);
    assert lang != null;
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
