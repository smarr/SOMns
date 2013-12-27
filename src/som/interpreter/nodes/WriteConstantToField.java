package som.interpreter.nodes;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class WriteConstantToField extends ExpressionNode {
  private final FrameSlot slot;
  private final Object value;

  public WriteConstantToField(final FrameSlot slot, final Object value) {
    this.slot  = slot;
    this.value = value;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    frame.setObject(slot, value);
    return value;
  }

  public Object getSlotIdentifier() {
    return slot.getIdentifier();
  }

  public Object getValue() {
    return value;
  }
}
