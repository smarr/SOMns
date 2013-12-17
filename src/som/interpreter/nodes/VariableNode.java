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

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.dsl.Generic;
import com.oracle.truffle.api.dsl.Specialization;
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

  public abstract static class VariableReadNode extends VariableNode {

    public VariableReadNode(final FrameSlot slot, final int contextLevel) {
      super(slot, contextLevel);
    }

    public VariableReadNode(final VariableReadNode node) {
      this(node.slot, node.contextLevel);
    }

    @SlowPath
    private void throwRuntimeException(final FrameSlot slot) {
      throw new RuntimeException("uninitialized variable " + slot.getIdentifier());
    }

    @Specialization(rewriteOn = {FrameSlotTypeException.class})
    public int doInteger(final VirtualFrame frame) throws FrameSlotTypeException {
      MaterializedFrame ctx = determineContext(frame.materialize());
      return ctx.getInt(slot);
    }

    @Specialization(rewriteOn = {FrameSlotTypeException.class})
    public double doDouble(final VirtualFrame frame) throws FrameSlotTypeException {
      MaterializedFrame ctx = determineContext(frame.materialize());
      return ctx.getDouble(slot);
    }

    @Generic
    public Object doGeneric(final VirtualFrame frame) {
      MaterializedFrame ctx = determineContext(frame.materialize());
      return ctx.getValue(slot);
    }
  }

  public abstract static class SelfReadNode extends VariableReadNode {
    public SelfReadNode(final FrameSlot slot, final int contextLevel) {
      super(slot, contextLevel); }

    public SelfReadNode(final SelfReadNode node) {
      this(node.slot, node.contextLevel); }
  }

  public abstract static class SuperReadNode extends VariableReadNode {
    public SuperReadNode(final FrameSlot slot, final int contextLevel) {
      super(slot, contextLevel); }

    public SuperReadNode(final SuperReadNode node) {
      this(node.slot, node.contextLevel); }
  }
}
