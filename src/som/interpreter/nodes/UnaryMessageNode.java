package som.interpreter.nodes;

import som.vm.Universe;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class UnaryMessageNode extends AbstractMessageNode {

  public UnaryMessageNode(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  public UnaryMessageNode(final UnaryMessageNode node) {
    this(node.selector, node.universe);
  }

  public abstract Object executeEvaluated(final VirtualFrame frame, final Object receiver);

//  TODO: rebuild support for does not understand!
//  @Specialization
//  public Object doGeneric(final VirtualFrame frame, final Object rcvr) {
//    CompilerDirectives.transferToInterpreter();
//
//    SAbstractObject receiver = (SAbstractObject) rcvr;
//
//    SClass rcvrClass = classOfReceiver(receiver);
//    SMethod invokable = rcvrClass.lookupInvokable(selector);
//
//    if (invokable != null) {
//      UnaryMonomorphicNode node = NodeFactory.createUnaryMonomorphicNode(selector, universe, rcvrClass, invokable, getReceiver());
//      return replace(node, "Be optimisitic and do a monomorphic lookup cache, or a primitive inline.").executeEvaluated(frame, receiver);
//    } else {
//      return doFullSend(frame, receiver, noArgs, rcvrClass);
//    }
//  }

}
