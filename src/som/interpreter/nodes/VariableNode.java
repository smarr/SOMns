package som.interpreter.nodes;

import som.vmobjects.Object;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class VariableNode extends ExpressionNode {
  
  protected final FrameSlot slot;
  
  public VariableNode(final FrameSlot slot) {
    this.slot = slot;
  }
  
  public static class VariableReadNode extends VariableNode {

    public VariableReadNode(final FrameSlot slot) {
      super(slot);
    }
    
    @Override
    public Object executeGeneric(VirtualFrame frame) {
      try {
        return (Object)frame.getObject(slot);
      } catch (FrameSlotTypeException e) {
        throw new RuntimeException("uninitialized variable " + slot.getIdentifier());
      }
    }

  }
  
  public static class SelfReadNode extends VariableReadNode {
    public SelfReadNode(final FrameSlot slot) { super(slot); }
  }
  
  public static class SuperReadNode extends VariableReadNode {
    public SuperReadNode(final FrameSlot slot) { super(slot); }
  }
  
  public static class VariableWriteNode extends VariableNode {
   
    protected final ExpressionNode exp;
    
    public VariableWriteNode(final FrameSlot slot,
        final ExpressionNode exp) {
      super(slot);
      this.exp = adoptChild(exp);
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
      Object result = exp.executeGeneric(frame);
      try {
        frame.setObject(slot, result);
      } catch (FrameSlotTypeException e) {
        throw new RuntimeException("Slot " + slot.getIdentifier() + " is of wrong type. Tried to assign som.Object, which is the only type we currently support.");
      }
      return result;
    }
  }

  
}
