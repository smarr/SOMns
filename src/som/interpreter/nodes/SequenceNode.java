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
    
    boolean restart;
    
    do {
      restart = false;

      try {
        for (ExpressionNode expression : expressions) {
          last = expression.executeGeneric(frame);
        }
      }
      catch (RestartLoopException e) {
        // TODO: figure out whether this is the best way to implement the
        // restart primitive, because it is in a pretty generic place for a
        // rather specific thing.
        restart = true;
      }
    } while (restart);
      
    return last;
  }

}
