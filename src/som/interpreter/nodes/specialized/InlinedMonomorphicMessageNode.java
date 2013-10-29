package som.interpreter.nodes.specialized;

import som.interpreter.Arguments;
import som.interpreter.Invokable;
import som.interpreter.nodes.ExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.Generic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.FrameFactory;
import com.oracle.truffle.api.nodes.InlinedCallSite;

public abstract class InlinedMonomorphicMessageNode extends AbstractInlinedMessageNode
  implements InlinedCallSite {

  public InlinedMonomorphicMessageNode(final SSymbol selector,
      final Universe universe, final SClass rcvrClass,
      final SMethod invokable,
      final FrameFactory frameFactory,
      final Invokable inlinedMethod, final ExpressionNode methodBody) {
    super(selector, universe, rcvrClass, invokable,
        frameFactory, inlinedMethod, methodBody);
  }

  public InlinedMonomorphicMessageNode(final InlinedMonomorphicMessageNode node) {
    this(node.selector, node.universe, node.rcvrClass, node.invokable,
        node.frameFactory, node.inlinedMethod, node.methodBody);
  }

  @Override
  public CallTarget getCallTarget() {
    return invokable.getCallTarget();
  }

  @Specialization(guards = "isCachedReceiverClass")
  public SAbstractObject doMonomorphic(final VirtualFrame caller, final SAbstractObject receiver,
      final Object arguments) {
    SAbstractObject[] args = (SAbstractObject[]) arguments;

    final VirtualFrame frame = frameFactory.create(
        inlinedMethod.getFrameDescriptor(), caller.pack(),
        new Arguments(receiver, args));

    return inlinedMethod.executeInlined(frame, methodBody);
  }

  @Generic
  public SAbstractObject doGeneric(final VirtualFrame frame, final SAbstractObject receiver,
      final Object arguments) {
    if (isCachedReceiverClass(receiver)) {
      return doMonomorphic(frame, receiver, arguments);
    } else {
      return generalizeNode(classOfReceiver(receiver, getReceiver())).
          doGeneric(frame, receiver, arguments);
    }
  }

  @Override
  public ExpressionNode cloneForInlining() {
    return InlinedMonomorphicMessageNodeFactory.create(selector, universe,
        rcvrClass, invokable, frameFactory, inlinedMethod, methodBody,
        getReceiver(), getArguments());
  }
}
