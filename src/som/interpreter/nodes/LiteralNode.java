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

import com.oracle.truffle.api.frame.VirtualFrame;

import som.vm.Universe;
import som.vmobjects.Method;
import som.vmobjects.Object;

public class LiteralNode extends ExpressionNode {

  protected final Object value;

  public LiteralNode(final Object value) {
    this.value = value;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return this.value;
  }

  public static class BlockNode extends LiteralNode {

    protected final Universe universe;

    public BlockNode(final Method blockMethod,
        final Universe universe) {
      super(blockMethod);
      this.universe = universe;
    }
    
    @Override
    public Object executeGeneric(VirtualFrame frame) {
      Method method = (Method)value;
      return universe.newBlock(method, frame, method.getNumberOfArguments());
    }
  }
}
