package som.interpreter.nodes.specialized;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageNode;
import som.vm.Universe;
import som.vmobjects.Class;
import som.vmobjects.Object;
import som.vmobjects.Symbol;

import com.oracle.truffle.api.frame.VirtualFrame;

public class MegamorphicMessageNode extends MessageNode {

  public MegamorphicMessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments, final Symbol selector,
      final Universe universe) {
    super(receiver, arguments, selector, universe);
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    // evaluate all the expressions: first determine receiver
    Object rcvr = receiver.executeGeneric(frame);

    // then determine the arguments
    Object[] args = determineArguments(frame);

    // now start lookup
    Class rcvrClass = classOfReceiver(rcvr, receiver);

    return doFullSend(frame, rcvr, args, rcvrClass);
  }
}
