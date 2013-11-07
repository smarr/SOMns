package som.interpreter.nodes;

import som.interpreter.nodes.VariableNode.SuperReadNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class AbstractMessageNode extends ExpressionNode {

  protected final SSymbol  selector;
  protected final Universe universe;

  public AbstractMessageNode(final SSymbol selector, final Universe universe) {
    this.selector = selector;
    this.universe = universe;
  }

  public AbstractMessageNode(final AbstractMessageNode node) {
    this(node.selector, node.universe);
  }

  protected SClass classOfReceiver(final SAbstractObject rcvr, final ExpressionNode receiver) {
    SClass rcvrClass = rcvr.getSOMClass(universe);

    // first determine whether it is a normal, or super send
    if (receiver instanceof SuperReadNode) {
      rcvrClass = (SClass) rcvrClass.getSuperClass();
    }
    return rcvrClass;
  }

  protected boolean isBooleanReceiver(final SAbstractObject receiver) {
    return receiver == universe.trueObject || receiver == universe.falseObject;
  }

  /**
   * Guard for system primitives.
   * TODO: make sure system primitives do not trigger on any other kind of object
   * @param receiver
   * @return
   */
  protected boolean receiverIsSystemObject(final SAbstractObject receiver) {
    return receiver == universe.systemObject;
  }

  protected SAbstractObject doFullSend(final VirtualFrame frame, final SAbstractObject rcvr,
      final SAbstractObject[] args, final SClass rcvrClass) {
    // now lookup selector
    SMethod invokable = rcvrClass.lookupInvokable(selector);

    if (invokable != null) {
      return invokable.invoke(frame.pack(), rcvr, args);
    } else {
      return rcvr.sendDoesNotUnderstand(selector, args, universe, frame.pack());
    }
  }

  protected static final SAbstractObject[] noArgs = new SAbstractObject[0];

  public static final int PriorityMonomorphicCase = 9999;
}
