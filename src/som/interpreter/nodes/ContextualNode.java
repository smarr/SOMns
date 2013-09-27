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

import som.compiler.MethodGenerationContext;
import som.vmobjects.Block;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;

public abstract class ContextualNode extends ExpressionNode {

  protected final int contextLevel;

  public ContextualNode(final int contextLevel) {
    this.contextLevel = contextLevel;
  }

  protected Block getBlockFromMaterialized(final MaterializedFrame ctx) {
    try {
      final FrameSlot blockSelfSlot = MethodGenerationContext.getStandardSelfSlot();
      return (Block) ctx.getObject(blockSelfSlot);
    } catch (FrameSlotTypeException e) {
      throw new RuntimeException("This should really really never happen...");
    }
  }

  protected MaterializedFrame determineContext(MaterializedFrame frame) {
    MaterializedFrame ctx = frame;
    if (contextLevel > 0) {
      int i = contextLevel;

      while (i > 0) {
        Block block = getBlockFromMaterialized(ctx);
        ctx = block.getContext();
        i--;
      }
    }
    return ctx;
  }

}
