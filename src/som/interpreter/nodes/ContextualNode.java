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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public abstract class ContextualNode extends ExpressionNode {

  protected final int            contextLevel;
  protected final FrameSlot      localSelf;

  public ContextualNode(final int contextLevel, final FrameSlot localSelf,
      final SourceSection source, final boolean executesEnforced) {
    super(source, executesEnforced);
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
  protected final MaterializedFrame determineContext(final VirtualFrame frame) {
    SBlock self = getLocalSelf(frame);
    int i = contextLevel - 1;

    while (i > 0) {
      self = CompilerDirectives.unsafeCast(self.getOuterSelf(), SBlock.class, true);
      i--;
    }
    return self.getContext();
  }

  private SBlock getLocalSelf(final VirtualFrame frame) {
    return CompilerDirectives.unsafeCast(FrameUtil.getObjectSafe(frame, localSelf), SBlock.class, true);
  }

  @ExplodeLoop
  protected final Object determineOuterSelf(final VirtualFrame frame) {
    Object self = getLocalSelf(frame);
    int i = contextLevel;
    while (i > 0) {
      SBlock block = CompilerDirectives.unsafeCast(self, SBlock.class, true);
      self = block.getOuterSelf();
      i--;
    }
    return self;
  }
}
