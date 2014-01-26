package som.interpreter.nodes;

import som.interpreter.Types;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild(value = "receiver", type = ExpressionNode.class)
public abstract class AbstractMessageNode extends ExpressionNode {

  protected static final int INLINE_CACHE_SIZE = 6;

  protected final SSymbol  selector;
  protected final Universe universe;

  public AbstractMessageNode(final SSymbol selector, final Universe universe) {
    this.selector = selector;
    this.universe = universe;
  }

  public AbstractMessageNode(final AbstractMessageNode node) {
    this(node.selector, node.universe);
  }

  public abstract ExpressionNode getReceiver();

  protected final SClass classOfReceiver(final Object rcvr) {
    // first determine whether it is a normal, or super send
    if (getReceiver() instanceof ISuperReadNode) {
      return ((ISuperReadNode) getReceiver()).getSuperClass();
    }

    return Types.getClassOf(rcvr, universe);
  }

  protected RootCallTarget lookupCallTarget(final Object rcvr) {
    SClass rcvrClass = classOfReceiver(rcvr);
    SMethod method = rcvrClass.lookupInvokable(selector);
    if (method == null) {
      return null;
    } else {
      return method.getCallTarget();
    }
  }

  protected SMethod lookupMethod(final Object rcvr) {
    SAbstractObject receiver = (SAbstractObject) rcvr;
    SClass rcvrClass = classOfReceiver(receiver);
    return rcvrClass.lookupInvokable(selector);
  }

  protected boolean isBooleanReceiver(final SAbstractObject receiver) {
    return receiver == universe.trueObject || receiver == universe.falseObject;
  }

  /**
   * Guard for system primitives.
   * @param receiver
   * @return
   */
  protected boolean receiverIsSystemObject(final SAbstractObject receiver) {
    return receiver == universe.systemObject;
  }

  protected Object doFullSend(final VirtualFrame frame, final SAbstractObject rcvr,
      final SAbstractObject[] args, final SClass rcvrClass) {
    // now lookup selector
    SMethod invokable = rcvrClass.lookupInvokable(selector);

    if (invokable != null) {
      return invokable.invoke(frame.pack(), rcvr, args, universe);
    } else {
      // TODO: mark as exceptional case
      return SAbstractObject.sendDoesNotUnderstand(rcvr, selector, args,
          universe, frame.pack());
    }
  }

  public static final int PriorityMonomorphicCase = 9999;
}
