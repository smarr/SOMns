package som.interpreter;

import som.interpreter.nodes.messages.UnarySendNode.InlinableUnarySendNode;
import som.vm.Universe;
import som.vmobjects.SMethod;


public final class BlockHelper {

  public static InlinableUnarySendNode createInlineableNode(final SMethod method, final Universe universe) {
    return new InlinableUnarySendNode(method.getSignature(),
        universe, method.getCallTarget(), method.getTruffleInvokable());
  }
}
