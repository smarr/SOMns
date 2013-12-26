package som.interpreter;

import som.interpreter.nodes.messages.UnarySendNode.InlinableUnarySendNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;


public final class BlockHelper {

  public static InlinableUnarySendNode createInlineableNode(final SMethod method, final Universe universe) {
    return new InlinableUnarySendNode(method.getSignature(),
        universe, method.getCallTarget(), method.getTruffleInvokable());
  }

  public static SBlock createBlock(final SBlock block, final Universe universe) {
    SMethod   method  = block.getMethod();
    Arguments context = block.getContext(); // TODO: test whether the current implementation is correct, or whether it should be the following: Method.getUpvalues(frame);
    return universe.newBlock(method, context);
  }
}
