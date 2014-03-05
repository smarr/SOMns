package som.interpreter;

import som.interpreter.nodes.messages.BinarySendNode.InlinableBinarySendNode;
import som.interpreter.nodes.messages.UnarySendNode.InlinableUnarySendNode;
import som.vm.Universe;
import som.vmobjects.SMethod;


public final class BlockHelper {

  public static InlinableUnarySendNode createInlineableNode(final SMethod method, final Universe universe) {
    return new InlinableUnarySendNode(method.getSignature(),
        universe, method.getCallTarget());
  }

  public static InlinableBinarySendNode createBinaryInlineableNode(final SMethod method, final Universe universe) {
    return new InlinableBinarySendNode(method.getSignature(),
        universe, method.getCallTarget());
  }
}
