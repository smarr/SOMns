package som.interpreter.nodes;

import som.interpreter.nodes.VariableNode.SuperReadNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.frame.VirtualFrame;


@NodeChildren({
  @NodeChild(value = "receiver",  type = ExpressionNode.class),
  @NodeChild(value = "arguments", type = ArgumentEvaluationNode.class)
})
public abstract class AbstractMessageNode extends ExpressionNode {

  protected final SSymbol  selector;
  protected final Universe universe;

  public AbstractMessageNode(final SSymbol selector, final Universe universe) {
    this.selector = selector;
    this.universe = universe;
  }

  public AbstractMessageNode(final MessageNode node) {
    this(node.selector, node.universe);
  }

  // TODO: document this getter idiom
  public abstract ExpressionNode getReceiver();
  public abstract ArgumentEvaluationNode getArguments();

  protected SClass classOfReceiver(final SAbstractObject rcvr, final ExpressionNode receiver) {
    SClass rcvrClass = rcvr.getSOMClass(universe);

    // first determine whether it is a normal, or super send
    if (receiver instanceof SuperReadNode) {
      rcvrClass = rcvrClass.getSuperClass();
    }
    return rcvrClass;
  }

  protected boolean hasOneArgument(final Object receiver, final Object arguments) {
    return (arguments != null && ((SAbstractObject[]) arguments).length == 1);
  }

  protected boolean hasTwoArguments(final Object receiver, final Object arguments) {
    return (arguments != null && ((SAbstractObject[]) arguments).length == 2);
  }

  protected boolean isBooleanReceiver(final SAbstractObject receiver) {
    SClass rcvrClass = classOfReceiver(receiver, getReceiver());
    return rcvrClass == universe.trueClass || rcvrClass == universe.falseClass;
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
}
