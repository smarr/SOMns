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
import som.vm.Universe;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class GlobalNode extends ExpressionNode {

  protected final SSymbol    globalName;
  protected final Universe  universe;

  public GlobalNode(final SSymbol globalName, final Universe  universe) {
    this.globalName = globalName;
    this.universe   = universe;
  }

  protected SObject getSelfFromVirtual(final VirtualFrame frame) {
    try {
      return (SObject) frame.getObject(MethodGenerationContext.getStandardSelfSlot());
    } catch (FrameSlotTypeException e) {
      throw new RuntimeException("uninitialized selfSlot, which should be pretty much imposible???");
    }
  }

  public static class GlobalReadNode extends GlobalNode {

    public GlobalReadNode(final SSymbol globalName, final Universe universe) {
      super(globalName, universe);
    }

    @Override
    public SObject executeGeneric(VirtualFrame frame) {
      // Get the global from the universe
      SObject global = universe.getGlobal(globalName);

      if (global != null) {
        return global;
      } else {
        // if it is not defined, we will send a error message to the current
        // receiver object
        SObject self = getSelfFromVirtual(frame);
        return self.sendUnknownGlobal(globalName, universe, frame.pack());
      }
    }
  }

  @Override
  public ExpressionNode cloneForInlining() {
    return this;
  }
}
