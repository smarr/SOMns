package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.vm.Universe;
import som.vmobjects.Method;
import som.vmobjects.Object;

public class LiteralNode extends ExpressionNode {
  protected final Object value;
  
  public LiteralNode(final Object value) {
    this.value = value;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return this.value;
  }
 
  public static class BlockNode extends LiteralNode {
    
    protected final Universe universe;
    
    public BlockNode(final Method blockMethod,
        final Universe universe) {
      super(blockMethod);
      this.universe = universe;
    }
    
    @Override
    public Object executeGeneric(VirtualFrame frame) {
      Method method = (Method)value;
      return universe.newBlock(method, frame, method.getNumberOfArguments());
    }
  }
  
}
