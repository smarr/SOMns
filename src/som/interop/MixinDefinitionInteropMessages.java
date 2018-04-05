package som.interop;

import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.nodes.Node;

import som.compiler.MixinDefinition;


@MessageResolution(receiverType = MixinDefinition.class)
public class MixinDefinitionInteropMessages {
  @Resolve(message = "IS_NULL")
  abstract static class NullCheckNode extends Node {
    public Object access(final MixinDefinition object) {
      return false;
    }
  }
}
