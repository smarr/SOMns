package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.interpreter.nodes.VariableNode.SuperReadNode;
import som.vm.Universe;
import som.vmobjects.Invokable;
import som.vmobjects.Object;
import som.vmobjects.Symbol;

// @NodeChildren({
//  @NodeChild(value = "receiver",  type = ExpressionNode.class),
//  @NodeChild(value = "arguments", type = ExpressionNode[].class)})
public class MessageNode extends ExpressionNode {
  
  @Child private final ExpressionNode   receiver;
  @Child private final ExpressionNode[] arguments;
  
  private final Symbol   selector;
  private final Universe universe;
  
  public MessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments,
      final Symbol selector,
      final Universe universe) {
    this.receiver  = adoptChild(receiver);
    this.arguments = adoptChildren(arguments);
    this.selector  = selector;
    this.universe  = universe;
  }
  
  @Override
  public Object executeGeneric(VirtualFrame frame) {
    // evaluate all the expressions: first determine receiver
    Object rcvr = receiver.executeGeneric(frame);
    int numArgs = (arguments == null) ? 0 : arguments.length;
    
    // then determine the arguments
    Object[] args = new Object[numArgs];
    
    for (int i = 0; i < numArgs; i++) {
      args[i] = arguments[i].executeGeneric(frame);
    }
    
    // now start lookup
    som.vmobjects.Class rcvrClass = rcvr.getSOMClass();
    
    // first determine whether it is a normal, or super send
    if (receiver instanceof SuperReadNode)
      rcvrClass = rcvrClass.getSuperClass();
    
    // now lookup selector
    Invokable invokable = rcvrClass.lookupInvokable(selector);
    
    if (invokable != null)
      return invokable.invoke(frame, rcvr, args);
    else
      return rcvr.sendDoesNotUnderstand(selector, args, universe, frame);
  }
  
}
