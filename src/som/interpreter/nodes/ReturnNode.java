package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.vmobjects.Object;

public class ReturnNode extends ContextualNode {

  // SHOULD BE THE LAST NODE OF A SEQUENCE/BLOCK/METHOD??? AND BE CREATED
  // EXPLICITLY (^) OR IMPLICITLY (end of code)
  
  @Child protected final ExpressionNode expression;
  
  public ReturnNode(final ExpressionNode expression, final int contextLevel) {
    super(contextLevel);
    this.expression = adoptChild(expression);
  }
  
  @Override
  public Object executeGeneric(VirtualFrame frame) {
    throw new RuntimeException("TODO: check do we use this node?");
    // throw new ReturnException(expression.executeGeneric(frame));
  }
  
  public static class ReturnNonLocalNode extends ReturnNode {

    public ReturnNonLocalNode(final ExpressionNode expression, final int contextLevel) {
      super(expression, contextLevel);
    }
    
    @Override
    public Object executeGeneric(VirtualFrame frame) {
      throw new ReturnException(expression.executeGeneric(frame),
          (VirtualFrame)determineContext(frame)); // .getCaller().unpack() TODO: cleanup
    }
    
  }
}
