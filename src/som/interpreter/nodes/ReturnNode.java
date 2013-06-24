package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.vmobjects.Object;

public class ReturnNode extends ExpressionNode {

  // SHOULD BE THE LAST NODE OF A SEQUENCE/BLOCK/METHOD??? AND BE CREATED
  // EXPLICITLY (^) OR IMPLICITLY (end of code)
  
  @Child protected ExpressionNode expression;
  
  public ReturnNode(ExpressionNode expression) {
    this.expression = adoptChild(expression);
  }
  
  @Override
  public Object executeGeneric(VirtualFrame frame) {
    throw new ReturnException(expression.executeGeneric(frame));
  }
  
  public static class ReturnNonLocalNode extends ReturnNode {

    public ReturnNonLocalNode(final ExpressionNode expression) {
      super(expression);
    }
    
    @Override
    public Object executeGeneric(VirtualFrame frame) {
      // TODO: add support for non-local returns!!!!
      System.out.println("TODO: IMPLEMENT NON-LOCAL RETURNS!");
      throw new ReturnException(expression.executeGeneric(frame));
    }
    
  }
}
