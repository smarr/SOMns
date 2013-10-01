package som.interpreter.nodes.specialized;

import som.interpreter.Method;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageNode;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.FrameFactory;
import com.oracle.truffle.api.nodes.InlinableCallSite;
import com.oracle.truffle.api.nodes.Node;

public class MonomorpicMessageNode extends MessageNode
  implements InlinableCallSite {

  private final SClass      rcvrClass;
  private final SInvokable  invokable;

  private int callCount;

  public MonomorpicMessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments, final SSymbol selector,
      final Universe universe, final SClass rcvrClass,
      final SInvokable invokable) {
    super(receiver, arguments, selector, universe);
    this.rcvrClass = rcvrClass;
    this.invokable = invokable;

    callCount = 0;
  }

  @Override
  public SObject executeGeneric(final VirtualFrame frame) {
    callCount++;

    // evaluate all the expressions: first determine receiver
    SObject rcvr = receiver.executeGeneric(frame);

    // then determine the arguments
    SObject[] args = determineArguments(frame);

    SClass currentRcvrClass = classOfReceiver(rcvr, receiver);

    if (currentRcvrClass == rcvrClass) {
      return invokable.invoke(frame.pack(), rcvr, args);
    } else {
      CompilerDirectives.transferToInterpreter();
      // So, it might just be a polymorphic send site.
      PolymorpicMessageNode poly = new PolymorpicMessageNode(receiver,
          arguments, selector, universe, rcvrClass, invokable, currentRcvrClass);

      replace(poly, "It is not a monomorpic send.");
      return doFullSend(frame, rcvr, args, currentRcvrClass);
    }
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

  @Override
  public Node getInlineTree() {
    Method method = invokable.getTruffleInvokable();
    if (method == null) {
      return this;
    }
    return method;
  }

  private InlinedMonomorphicMessageNode newInlinedNode(
      final FrameFactory frameFactory,
      final Method method) {
    return new InlinedMonomorphicMessageNode(receiver, arguments, selector,
        universe, rcvrClass, invokable,
        frameFactory, method, method.methodCloneForInlining());
  }

  @Override
  public boolean inline(FrameFactory factory) {
    Method method = invokable.getTruffleInvokable();
    if (method == null) {
      return false;
    }

    InlinedMonomorphicMessageNode inlinedNode = newInlinedNode(factory, method);

    replace(inlinedNode, "Node got inlined");

    return true;
  }
}
