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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.dsl.Generic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.FrameFactory;

public abstract class KeywordMonomorphicNode extends AbstractMonomorphicMessageNode {

  public KeywordMonomorphicNode(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) {
    super(selector, universe, rcvrClass, invokable);
  }

  public KeywordMonomorphicNode(final KeywordMonomorphicNode node) {
    this(node.selector, node.universe, node.rcvrClass, node.invokable);
  }

  public abstract Object executeEvaluated(final VirtualFrame frame, final Object receiver, Object[] arguments);

  @Override
  public ExpressionNode cloneForInlining() {
    return NodeFactory.createKeywordMonomorphicNode(selector, universe, rcvrClass, invokable, getReceiver(), getArguments());
  }

  @Specialization(guards = "isCachedReceiverClass")
  public SAbstractObject doMonomorphic(final VirtualFrame frame, final SAbstractObject receiver,
      final Object[] arguments) {
    callCount++;
    SAbstractObject[] args = (SAbstractObject[]) arguments;
    return invokable.invoke(frame.pack(), receiver, args);
  }

  @Generic
  public Object doGeneric(final VirtualFrame frame, final Object receiver,
      final Object[] arguments) {
    if (isCachedReceiverClass((SAbstractObject) receiver)) {
      throw new IllegalStateException("Don't know how that could happen?");
    }

    CompilerDirectives.transferToInterpreter();
    KeywordPolymorphicMessageNode node = KeywordPolymorphicMessageNodeFactory.create(selector, universe, rcvrClass, getReceiver(), getArguments());
    return replace(node, "It is not a monomorpic send.").executeEvaluated(frame, receiver, arguments);
  }


  // **** TODO:

  @SlowPath
  private KeywordInlinedMessageNode newInlinedNode(
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
