package som.interpreter.nodes;

import som.compiler.Variable.Local;

import com.oracle.truffle.api.dsl.Generic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class LocalVariableNode extends ExpressionNode {
  protected final FrameSlot slot;

  private LocalVariableNode(final FrameSlot slot) {
    this.slot = slot;
  }

  public abstract static class LocalVariableReadNode extends LocalVariableNode {
    public LocalVariableReadNode(final Local variable) {
      super(variable.slot);
    }

    public LocalVariableReadNode(final LocalVariableReadNode node) {
      super(node.slot);
    }

    @Specialization(rewriteOn = {FrameSlotTypeException.class})
    public int doInteger(final VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getInt(slot);
    }

    @Specialization(rewriteOn = {FrameSlotTypeException.class})
    public double doDouble(final VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getDouble(slot);
    }

    @Generic
    public Object doGeneric(final VirtualFrame frame) {
      return frame.getValue(slot);
    }
  }

  @NodeChild(value = "exp", type = ExpressionNode.class)
  public abstract static class LocalVariableWriteNode extends LocalVariableNode {

    public LocalVariableWriteNode(final Local variable) {
      super(variable.slot);
    }

    public LocalVariableWriteNode(final LocalVariableWriteNode node) {
      super(node.slot);
    }

    @Specialization(rewriteOn = FrameSlotTypeException.class)
    public int write(final VirtualFrame frame, final int expValue) throws FrameSlotTypeException {
      frame.setInt(slot, expValue);
      return expValue;
    }

    @Specialization(rewriteOn = FrameSlotTypeException.class)
    public double write(final VirtualFrame frame, final double expValue) throws FrameSlotTypeException {
      frame.setDouble(slot, expValue);
      return expValue;
    }

    @Generic
    public Object writeGeneric(final VirtualFrame frame, final Object expValue) {
      frame.setObject(slot, expValue);
      return expValue;
    }
  }
}
