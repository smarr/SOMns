package som.interpreter.nodes.specialized;

import som.interpreter.nodes.AbstractMessageNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.NodeFactory;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class MegamorphicMessageNode extends AbstractMessageNode {

  public MegamorphicMessageNode(final SSymbol selector,
      final Universe universe) {
    super(selector, universe);
  }

  @Specialization
  public SObject doGeneric(final VirtualFrame frame, final SObject receiver,
      final Object arguments) {
    SClass rcvrClass = classOfReceiver(receiver, getReceiver());
    SObject[] args   = ((SObject[]) arguments);

    return doFullSend(frame, receiver, args, rcvrClass);
  }

  @Override
  public ExpressionNode cloneForInlining() {
    return NodeFactory.createMessageNode(selector, universe,
        getReceiver().cloneForInlining(),
        getArguments().cloneForInlining());
  }
}
