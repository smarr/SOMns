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
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.FrameFactory;

@NodeChild(value = "receiver", type = ExpressionNode.class)
public abstract class UnaryMonomorphicNode extends AbstractMonomorphicMessageNode {

  public UnaryMonomorphicNode(final SSymbol selector, final Universe universe,
      final SClass rcvrClass, final SMethod invokable) {
    super(selector, universe, rcvrClass, invokable);
  }

  public UnaryMonomorphicNode(final UnaryMonomorphicNode node) {
    this(node.selector, node.universe, node.rcvrClass, node.invokable);
  }

  // TODO: document these `idioms`!!! needs to include frame parameter if you want to use it...
  public abstract Object executeEvaluated(final VirtualFrame frame, final Object receiver);

  @Override
  public ExpressionNode cloneForInlining() {
    return NodeFactory.createUnaryMonomorphicNode(selector, universe, rcvrClass, invokable, getReceiver());
  }

  @Specialization(guards = "isCachedReceiverClass", order = PriorityMonomorphicCase)
  public SAbstractObject doMonomorphic(final VirtualFrame frame, final SAbstractObject receiver) {
    callCount++;
    return invokable.invoke(frame.pack(), receiver, noArgs);
  }

  // TODO: generic case rewrite to polymorphic, or let grow polymorphic based on DSL???

  @Generic
  public Object doGeneric(final VirtualFrame frame, final Object receiver) {
    if (isCachedReceiverClass((SAbstractObject) receiver)) {
      throw new IllegalStateException("Don't know how that could happen?");
    }

    UnaryPolymorphicMessageNode poly = UnaryPolymorphicMessageNodeFactory.create(selector, universe, rcvrClass, getReceiver());
    return replace(poly, "It is not a monomorpic send.").executeEvaluated(frame, receiver);
  }

  // **** TODO:

  @SlowPath
  private UnaryInlinedMessageNode newInlinedNode(
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
