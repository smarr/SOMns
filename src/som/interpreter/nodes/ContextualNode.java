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

import som.interpreter.Arguments;
import som.vmobjects.SBlock;

import com.oracle.truffle.api.frame.VirtualFrame;


// TODO: this class is to be removed and replaced by UpValues
public abstract class ContextualNode extends ExpressionNode {

  protected final int contextLevel;

  public ContextualNode(final int contextLevel) {
    this.contextLevel = contextLevel;
  }

  protected Arguments determineOuterArguments(final VirtualFrame frame) {
    Arguments args = Arguments.get(frame);
    if (contextLevel > 0) {
      int i = contextLevel;

      while (i > 0) {
        SBlock block = (SBlock) args.getSelf();
        args = block.getContext();
        i--;
      }
    }
    return args;
  }

  protected Object determineOuterSelf(final VirtualFrame frame) {
    Object self = Arguments.get(frame).getSelf();
    if (contextLevel > 0) {
      int i = contextLevel;

      while (i > 0) {
        SBlock block = (SBlock) self;
        self = block.getOuterSelf();
        i--;
      }
    }
    return self;
  }
}
