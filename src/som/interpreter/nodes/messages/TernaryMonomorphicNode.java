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
  @NodeChild(value = "receiver",  type = ExpressionNode.class), // TODO: shouldn't that one be inherited??
  @NodeChild(value = "firstArg",  type = ExpressionNode.class),
  @NodeChild(value = "secondArg", type = ExpressionNode.class)
})
public abstract class TernaryMonomorphicNode extends AbstractMonomorphicMessageNode {

  public TernaryMonomorphicNode(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) {
    super(selector, universe, rcvrClass, invokable);
  }

  public TernaryMonomorphicNode(final TernaryMonomorphicNode node) {
    this(node.selector, node.universe, node.rcvrClass, node.invokable);
  }

  // TODO: document this getter idiom
  public abstract ExpressionNode getFirstArg();
  public abstract ExpressionNode getSecondArg();
  public abstract Object executeEvaluated(final VirtualFrame frame, final Object receiver, Object firstArg, Object secondArg);

  @Override
  public ExpressionNode cloneForInlining() {
    return NodeFactory.createTernaryMonomorphicNode(selector, universe, rcvrClass, invokable,
        getReceiver(), getFirstArg(), getSecondArg());
  }

  @Specialization(guards = "isCachedReceiverClass", order = PriorityMonomorphicCase)
  public SAbstractObject doMonomorphic(final VirtualFrame frame, final SAbstractObject receiver,
      final SAbstractObject firstArg, final SAbstractObject secondArg) {
    callCount++;
    SAbstractObject[] args = new SAbstractObject[] {firstArg, secondArg};
    return invokable.invoke(frame.pack(), receiver, args);
  }

  // TODO: generic case rewrite to polymorphic, or let grow polymorphic based on DSL???
  @Generic
  public Object doGeneric(final VirtualFrame frame, final Object receiver,
      final Object firstArg, final Object secondArg) {
    if (isCachedReceiverClass((SAbstractObject) receiver)) {
      throw new IllegalStateException("Don't know how that could happen?");
    }

    TernaryPolymorphicMessageNode poly = TernaryPolymorphicMessageNodeFactory.create(selector, universe, rcvrClass, getReceiver(), getFirstArg(), getSecondArg());
    return replace(poly, "It is not a monomorpic send.").executeEvaluated(frame, receiver, firstArg, secondArg);
  }

  // **** TODO:

  @SlowPath
  private TernaryInlinedMessageNode newInlinedNode(
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
