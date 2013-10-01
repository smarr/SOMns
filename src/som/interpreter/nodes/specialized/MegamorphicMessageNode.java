package som.interpreter.nodes.specialized;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageNode;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;

public class MegamorphicMessageNode extends MessageNode {

  public MegamorphicMessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments, final SSymbol selector,
      final Universe universe) {
    super(receiver, arguments, selector, universe);
  }

  @Override
  public SObject executeGeneric(final VirtualFrame frame) {
    // evaluate all the expressions: first determine receiver
    SObject rcvr = receiver.executeGeneric(frame);

    // then determine the arguments
    SObject[] args = determineArguments(frame);

    // now start lookup
    SClass rcvrClass = classOfReceiver(rcvr, receiver);

    return doFullSend(frame, rcvr, args, rcvrClass);
  }
}
