package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.vmobjects.Object;

public class SequenceNode extends ExpressionNode {
  @Children private final ExpressionNode[] expressions;
  
  public SequenceNode(ExpressionNode[] expressions) {
    this.expressions = adoptChildren(expressions);
  }
  
  @Override
  public Object executeGeneric(VirtualFrame frame) {
    Object last = null;
    
    for (ExpressionNode expression : expressions) {
      last = expression.executeGeneric(frame);
    }
    
    return last;
  }

}
