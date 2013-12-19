package som.interpreter.nodes;

import som.compiler.Variable.Local;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class LocalVariableNode extends ExpressionNode {
  protected final FrameSlot slot;

  private LocalVariableNode(final FrameSlot slot) {
    this.slot = slot;
  }

  public static class LocalVariableReadNode extends LocalVariableNode {
    public LocalVariableReadNode(final Local variable) {
      super(variable.slot);
    }

    @SlowPath
    private void throwRuntimeException(final FrameSlot slot) {
      throw new RuntimeException("uninitialized variable " + slot.getIdentifier());
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      try {
        Object value = frame.getObject(slot);
        if (value == null) {
          throwRuntimeException(slot);
        }
        return value;
      } catch (FrameSlotTypeException e) {
        throwRuntimeException(slot);
        return null;
      }
    }
  }

  public static class LocalVariableWriteNode extends LocalVariableNode {
    @Child private ExpressionNode exp;

    public LocalVariableWriteNode(final Local variable, final ExpressionNode exp) {
      super(variable.slot);
      this.exp = adoptChild(exp);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object result = exp.executeGeneric(frame);
      frame.setObject(slot, result);
      return result;
    }
  }
}
