package som.interpreter.nodes.messages;

import som.interpreter.Invokable;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.NodeFactory;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.dsl.Generic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.FrameFactory;

@NodeChildren({
  @NodeChild(value = "receiver", type = ExpressionNode.class), // TODO: shouldn't that one be inherited??
  @NodeChild(value = "argument", type = ExpressionNode.class)
})
public abstract class BinaryMonomorphicNode extends AbstractMonomorphicMessageNode {

  public BinaryMonomorphicNode(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) {
    super(selector, universe, rcvrClass, invokable);
  }

  public BinaryMonomorphicNode(final BinaryMonomorphicNode node) {
    this(node.selector, node.universe, node.rcvrClass, node.invokable);
  }

  public abstract ExpressionNode getArgument();
  public abstract Object executeEvaluated(final VirtualFrame frame, final Object receiver, Object argument);

  @Override
  public ExpressionNode cloneForInlining() {
    return NodeFactory.createBinaryMonomorphicNode(selector, universe, rcvrClass, invokable, getReceiver(), getArgument());
  }

  @Specialization(guards = "isCachedReceiverClass")
  public SAbstractObject doMonomorphic(final VirtualFrame frame, final SAbstractObject receiver,
      final SAbstractObject argument) {
    callCount++;
    SAbstractObject[] args = new SAbstractObject[] {argument};
    return invokable.invoke(frame.pack(), receiver, args);
  }

  @Generic
  public Object doGeneric(final VirtualFrame frame, final Object receiver,
      final Object argument) {
    if (isCachedReceiverClass((SAbstractObject) receiver)) {
      throw new IllegalStateException("Don't know how that could happen?");
    }

    BinaryPolymorphicMessageNode poly = BinaryPolymorphicMessageNodeFactory.create(selector, universe, rcvrClass, getArgument(), getReceiver());
    return replace(poly, "It is not a monomorpic send.").executeEvaluated(frame, receiver, argument);
  }


  // **** TODO:

  @SlowPath
  private BinaryInlinedMessageNode newInlinedNode(
      final FrameFactory frameFactory,
      final Invokable method) {
    throw new NotImplementedException(); // TODO: implement...
//    return InlinedMonomorphicMessageNodeFactory.create(selector, universe, rcvrClass, invokable, frameFactory,
//        method, method.methodCloneForInlining(), getReceiver(), noArgs);
  }

  @SlowPath
  @Override
  public boolean inline(final FrameFactory factory) {
    throw new NotImplementedException(); // TODO: implement...

//    Invokable method = invokable.getTruffleInvokable();
//    if (method == null) {
//      return false;
//    }
//
//    InlinedMonomorphicMessageNode inlinedNode = newInlinedNode(factory, method);
//
//    replace(inlinedNode, "Node got inlined");
//
//    return true;
  }
}
