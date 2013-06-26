package som.interpreter.nodes;

import som.vmobjects.Object;
import som.vmobjects.Symbol;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class FieldNode extends ContextualNode {

  protected final Symbol    fieldName;
  protected final FrameSlot selfSlot;
  
  public FieldNode(final Symbol fieldName,
      final FrameSlot selfSlot,
      final int contextLevel) {
    super(contextLevel);
    this.fieldName = fieldName;
    this.selfSlot  = selfSlot;
  }

  public static class FieldReadNode extends FieldNode {
    
    public FieldReadNode(final Symbol fieldName,
        final FrameSlot selfSlot,
        final int contextLevel) {
      super(fieldName, selfSlot, contextLevel);
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
      Object self;
      try {
        VirtualFrame ctx = determineContext(frame);
        self = (Object)ctx.getObject(selfSlot);
        
        int fieldIndex = self.getFieldIndex(fieldName);
        return self.getField(fieldIndex);
      } catch (FrameSlotTypeException e) {
        throw new RuntimeException("uninitialized selfSlot, which should be pretty much imposible???");
      }
    }
    
  }
  
  public static class FieldWriteNode extends FieldNode {
    
    protected final ExpressionNode exp;
    
    public FieldWriteNode(final Symbol fieldName,
        final FrameSlot selfSlot,
        final int contextLevel,
        final ExpressionNode exp) {
      super(fieldName, selfSlot, contextLevel);
      this.exp = adoptChild(exp);
    }
    
    @Override
    public Object executeGeneric(VirtualFrame frame) {
      Object self;
      Object value;
      try {
        VirtualFrame ctx = determineContext(frame);
        value = exp.executeGeneric(frame);
        self  = (Object)ctx.getObject(selfSlot);
        
        int fieldIndex = self.getFieldIndex(fieldName);
        self.setField(fieldIndex, value);
        return value;
      } catch (FrameSlotTypeException e) {
        throw new RuntimeException("uninitialized selfSlot, which should be pretty much imposible???");
      }      
    }
    
  }
}
