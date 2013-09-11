/**
 * Copyright (c) 2013 Stefan Marr, stefan.marr@vub.ac.be
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package som.interpreter.nodes;

import som.vmobjects.Object;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class VariableNode extends ContextualNode {

  protected final FrameSlot slot;

  public VariableNode(final FrameSlot slot, final int contextLevel) {
    super(contextLevel);
    this.slot = slot;
  }

  public static class VariableReadNode extends VariableNode {

    public VariableReadNode(final FrameSlot slot, final int contextLevel) {
      super(slot, contextLevel);
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
      MaterializedFrame ctx = determineContext(frame.materialize());

      try {
        Object value = (Object) ctx.getObject(slot);
        if (value == null) {
          throw new RuntimeException("uninitialized variable " + slot.getIdentifier());
        }
        return value;
      } catch (FrameSlotTypeException e) {
        throw new RuntimeException("uninitialized variable " + slot.getIdentifier());
      }
    }

    @Override
    public ExpressionNode cloneForInlining() {
      return this;
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
    @Child protected final ExpressionNode exp;

    public VariableWriteNode(final FrameSlot slot, final int contextLevel,
        final ExpressionNode exp) {
      super(slot, contextLevel);
      this.exp = adoptChild(exp);
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
      Object result = exp.executeGeneric(frame);

      MaterializedFrame ctx = determineContext(frame.materialize());

      try {
        ctx.setObject(slot, result);
      } catch (FrameSlotTypeException e) {
        throw new RuntimeException("Slot " + slot.getIdentifier() + " is of wrong type. Tried to assign som.Object, which is the only type we currently support.");
      }
      return result;
    }

    @Override
    public ExpressionNode cloneForInlining() {
      return new VariableWriteNode(slot, contextLevel, exp.cloneForInlining());
    }
  }
}
