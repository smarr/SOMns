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
import som.interpreter.FrameOnStackMarker;
import som.interpreter.ReturnException;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;

import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

public class ReturnNonLocalNode extends ContextualNode {

  @Child private ExpressionNode expression;
  private final Universe universe;

  public ReturnNonLocalNode(final ExpressionNode expression,
      final int contextLevel,
      final Universe universe) {
    super(contextLevel);
    this.expression = adoptChild(expression);
    this.universe   = universe;
  }

  private FrameOnStackMarker getMarker(final MaterializedFrame ctx) {
    try {
      return (FrameOnStackMarker) ctx.getObject(MethodGenerationContext.
        getStandardNonLocalReturnMarkerSlot());
    } catch (FrameSlotTypeException e) {
      throw new RuntimeException("This should never happen! really!");
    }
  }

  private SBlock getBlockFromVirtual(final VirtualFrame frame) {
    try {
      return (SBlock) frame.getObject(MethodGenerationContext.getStandardSelfSlot());
    } catch (FrameSlotTypeException e) {
      throw new RuntimeException("This should never happen! really!");
    }
  }

  private Object getSelf(final MaterializedFrame ctx) {
    try {
      return ctx.getObject(MethodGenerationContext.getStandardSelfSlot());
    } catch (FrameSlotTypeException e) {
      throw new RuntimeException("This should never happen! really!");
    }
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    MaterializedFrame ctx = determineContext(frame.materialize());
    FrameOnStackMarker marker = getMarker(ctx);

    if (marker.isOnStack()) {
      Object result = expression.executeGeneric(frame);
      throw new ReturnException(result, marker);
    } else {
      SBlock block = getBlockFromVirtual(frame);
      Object self = getSelf(ctx);
      return SAbstractObject.sendEscapedBlock(self, block, universe, frame.pack());
    }
  }
}
