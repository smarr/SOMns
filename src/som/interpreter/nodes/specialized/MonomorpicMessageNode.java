package som.interpreter.nodes.specialized;

import som.interpreter.Invokable;
import som.interpreter.nodes.AbstractMessageNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.NodeFactory;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.dsl.Generic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.FrameFactory;
import com.oracle.truffle.api.nodes.InlinableCallSite;
import com.oracle.truffle.api.nodes.Node;

public abstract class MonomorpicMessageNode extends AbstractMessageNode
  implements InlinableCallSite {

  private final SClass  rcvrClass;
  private final SMethod invokable;

  private int callCount;

  public MonomorpicMessageNode(final SSymbol selector, final Universe universe,
      final SClass rcvrClass, final SMethod invokable) {
    super(selector, universe);
    this.rcvrClass = rcvrClass;
    this.invokable = invokable;

    callCount = 0;
  }

  public MonomorpicMessageNode(final MonomorpicMessageNode node) {
    this(node.selector, node.universe, node.rcvrClass, node.invokable);
  }

  public boolean isCachedReceiverClass(final SObject receiver) {
    SClass currentRcvrClass = classOfReceiver(receiver, getReceiver());
    return currentRcvrClass == rcvrClass;
  }

  @Specialization(guards = "isCachedReceiverClass")
  public SObject doMonomorphic(final VirtualFrame frame, final SObject receiver,
      final Object arguments) {
    callCount++;
    SObject[] args = (SObject[]) arguments;
    return invokable.invoke(frame.pack(), receiver, args);
  }

  @Generic
  public SObject doGeneric(final VirtualFrame frame, final SObject receiver,
      final Object arguments) {

    if (isCachedReceiverClass(receiver)) {
      return doMonomorphic(frame, receiver, arguments);
    } else {
      return generalizeToPolymorphicNode().
          doGeneric(frame, receiver, arguments);
    }
  }

  public PolymorpicMessageNode generalizeToPolymorphicNode() {
    CompilerDirectives.transferToInterpreter();
    // So, it might just be a polymorphic send site.
    PolymorpicMessageNode poly = PolymorpicMessageNodeFactory.create(selector,
        universe, rcvrClass, getReceiver(), getArguments());
    return replace(poly, "It is not a monomorpic send.");
  }

  @Override
  public int getCallCount() {
    return callCount;
  }

  @Override
  public void resetCallCount() {
    callCount = 0;
  }

  @Override
  public CallTarget getCallTarget() {
    return invokable.getCallTarget();
  }

  @SlowPath
  @Override
  public Node getInlineTree() {
    Invokable method = invokable.getTruffleInvokable();
    if (method == null) {
      return this;
    }
    return method;
  }

  @SlowPath
  private InlinedMonomorphicMessageNode newInlinedNode(
      final FrameFactory frameFactory,
      final Invokable method) {
    return InlinedMonomorphicMessageNodeFactory.create(selector, universe, rcvrClass, invokable, frameFactory,
        method, method.methodCloneForInlining(), getReceiver(), getArguments());
  }

  @SlowPath
  @Override
  public boolean inline(final FrameFactory factory) {
    Invokable method = invokable.getTruffleInvokable();
    if (method == null) {
      return false;
    }

    InlinedMonomorphicMessageNode inlinedNode = newInlinedNode(factory, method);

    replace(inlinedNode, "Node got inlined");

    return true;
  }

  @Override
  public ExpressionNode cloneForInlining() {
    return NodeFactory.createMessageNode(selector, universe,
        getReceiver().cloneForInlining(),
        getArguments().cloneForInlining());
  }
}
