package som.interpreter.nodes;

import som.vmobjects.Block;
import som.vmobjects.Object;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class VariableNode extends ExpressionNode {
  
  protected final FrameSlot slot;
  protected final int contextLevel;
  
  public VariableNode(final FrameSlot slot, final int contextLevel) {
    this.slot = slot;
    this.contextLevel = contextLevel;
  }
  
  protected VirtualFrame determineContext(VirtualFrame frame) {
    VirtualFrame ctx = frame;
    if (contextLevel > 0) {
      int i = contextLevel;
      
      while (i > 0) {
        try {
          FrameSlot blockSelfSlot = ctx.getFrameDescriptor().findFrameSlot("self");
          Block block = (Block)ctx.getObject(blockSelfSlot);
          ctx = block.getContext();
          i--;
        } catch (FrameSlotTypeException e) {
          throw new RuntimeException("This should really really never happen...");
        }
      }
    }
    return ctx;
  }
  
  public static class VariableReadNode extends VariableNode {

    public VariableReadNode(final FrameSlot slot, final int contextLevel) {
      super(slot, contextLevel);
    }
    
    @Override
    public Object executeGeneric(VirtualFrame frame) {
      VirtualFrame ctx = determineContext(frame);

      try {
        return (Object)ctx.getObject(slot);
      } catch (FrameSlotTypeException e) {
        throw new RuntimeException("uninitialized variable " + slot.getIdentifier());
      }
    }

  }
  
  public static class SelfReadNode extends VariableReadNode {
    public SelfReadNode(final FrameSlot slot, final int contextLevel) {
      super(slot, contextLevel); }
  }
  
  public static class SuperReadNode extends VariableReadNode {
    public SuperReadNode(final FrameSlot slot, final int contextLevel) {
      super(slot, contextLevel); }
  }
  
  public static class VariableWriteNode extends VariableNode {
   
    protected final ExpressionNode exp;
    
    public VariableWriteNode(final FrameSlot slot, final int contextLevel,
        final ExpressionNode exp) {
      super(slot, contextLevel);
      this.exp = adoptChild(exp);
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
      Object result = exp.executeGeneric(frame);
      
      VirtualFrame ctx = determineContext(frame);
      
      try {
        ctx.setObject(slot, result);
      } catch (FrameSlotTypeException e) {
        throw new RuntimeException("Slot " + slot.getIdentifier() + " is of wrong type. Tried to assign som.Object, which is the only type we currently support.");
      }
      return result;
    }
  }

  
}
