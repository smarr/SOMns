package som.interpreter.nodes.specialized;

import som.interpreter.Arguments;
import som.interpreter.nodes.BinaryMessageNode;
import som.interpreter.nodes.messages.UnarySendNode.InlinableUnarySendNode;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;


public abstract class AbstractBinaryBlockInliningNode extends BinaryMessageNode {
  public AbstractBinaryBlockInliningNode(final BinaryMessageNode node) {
    super(node);
  }

  protected final InlinableUnarySendNode createInlineableNode(final SMethod method) {
    return new InlinableUnarySendNode(method.getSignature(),
        universe, method.getCallTarget(), method.getTruffleInvokable());
  }

  protected final SBlock createBlock(final SBlock block) {
    SMethod   method  = block.getMethod();
    Arguments context = block.getContext(); // TODO: test whether the current implementation is correct, or whether it should be the following: Method.getUpvalues(frame);
    return universe.newBlock(method, context);
  }
}
