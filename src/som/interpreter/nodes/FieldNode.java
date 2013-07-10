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
import som.vmobjects.Symbol;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
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
        MaterializedFrame ctx = determineContext(frame.materialize());
        self = (Object) ctx.getObject(selfSlot);

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
        MaterializedFrame ctx = determineContext(frame.materialize());
        value = exp.executeGeneric(frame);
        self  = (Object) ctx.getObject(selfSlot);

        int fieldIndex = self.getFieldIndex(fieldName);
        self.setField(fieldIndex, value);
        return value;
      } catch (FrameSlotTypeException e) {
        throw new RuntimeException("uninitialized selfSlot, which should be pretty much imposible???");
      }
    }
  }
}
