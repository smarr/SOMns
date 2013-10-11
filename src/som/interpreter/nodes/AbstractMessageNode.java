package som.interpreter.nodes;

import som.interpreter.nodes.VariableNode.SuperReadNode;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
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

  protected static SClass classOfReceiver(final SObject rcvr, final ExpressionNode receiver) {
    SClass rcvrClass = rcvr.getSOMClass();

    // first determine whether it is a normal, or super send
    if (receiver instanceof SuperReadNode) {
      rcvrClass = rcvrClass.getSuperClass();
    }
    return rcvrClass;
  }

  protected boolean hasOneArgument(final Object receiver, final Object arguments) {
    return (arguments != null && ((SObject[]) arguments).length == 1);
  }

  protected boolean isBooleanReceiver(final SObject receiver) {
    SClass rcvrClass = classOfReceiver(receiver, getReceiver());
    return rcvrClass == universe.trueClass || rcvrClass == universe.falseClass;
  }

  protected SObject doFullSend(final VirtualFrame frame, final SObject rcvr,
      final SObject[] args, final SClass rcvrClass) {
    // now lookup selector
    SInvokable invokable = rcvrClass.lookupInvokable(selector);

    if (invokable != null) {
      return invokable.invoke(frame.pack(), rcvr, args);
    } else {
      return rcvr.sendDoesNotUnderstand(selector, args, universe, frame.pack());
    }
  }
}
