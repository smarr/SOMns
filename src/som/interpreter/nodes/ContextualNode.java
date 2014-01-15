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

import som.vmobjects.SBlock;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public abstract class ContextualNode extends ExpressionNode {

  protected final int            contextLevel;
  protected final FrameSlot      localSelf;

  public ContextualNode(final int contextLevel, final FrameSlot localSelf) {
    this.contextLevel = contextLevel;
    this.localSelf    = localSelf;
  }

  protected final Object getLocalSelfSlotIdentifier() {
    return localSelf.getIdentifier();
  }

  public final int getContextLevel() {
    return contextLevel;
  }

  public final boolean accessesOuterContext() {
    return contextLevel > 0;
  }

  @ExplodeLoop
  protected MaterializedFrame determineContext(final VirtualFrame frame) {
    // TODO: this doesn't look optimal, can we get rid of last assignment to self in the loop?
    Object self = getLocalSelf(frame);
    int i = contextLevel;
    MaterializedFrame ctx = frame.materialize();
    while (i > 0) {
      ctx  = ((SBlock) self).getContext();
      self = ((SBlock) self).getOuterSelf();
      i--;
    }
    return ctx;
  }

  private SBlock getLocalSelf(final VirtualFrame frame) {
    return (SBlock) FrameUtil.getObjectSafe(frame, localSelf);
  }

  @ExplodeLoop
  protected Object determineOuterSelf(final VirtualFrame frame) {
    Object self = getLocalSelf(frame);
    int i = contextLevel;
    while (i > 0) {
      SBlock block = (SBlock) self;
      self = block.getOuterSelf();
      i--;
    }
    return self;
  }
}
