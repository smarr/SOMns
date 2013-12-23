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
import som.interpreter.FrameOnStackMarker;
import som.interpreter.ReturnException;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.utilities.BranchProfile;

public class ReturnNonLocalNode extends ContextualNode {

  @Child private ExpressionNode expression;
  private final Universe universe;
  private final BranchProfile blockEscaped;

  public ReturnNonLocalNode(final ExpressionNode expression,
      final int contextLevel,
      final Universe universe) {
    super(contextLevel);
    this.expression = adoptChild(expression);
    this.universe   = universe;
    this.blockEscaped = new BranchProfile();
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    Arguments outer = determineOuterArguments(frame);
    FrameOnStackMarker marker = outer.getFrameOnStackMarker();

    if (marker.isOnStack()) {
      Object result = expression.executeGeneric(frame);
      throw new ReturnException(result, marker);
    } else {
      blockEscaped.enter();
      SBlock block = (SBlock) Arguments.get(frame).getSelf();
      Object self = outer.getSelf();
      return SAbstractObject.sendEscapedBlock(self, block, universe, frame.pack());
    }
  }

  public static class CatchNonLocalReturnNode extends ExpressionNode {
    @Child protected ExpressionNode methodBody;

    public CatchNonLocalReturnNode(final ExpressionNode methodBody) {
      this.methodBody = methodBody;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      FrameOnStackMarker marker = Arguments.get(frame).getFrameOnStackMarker();
      Object result;

      try {
        result = methodBody.executeGeneric(frame);
      } catch (ReturnException e) {
        if (!e.reachedTarget(marker)) {
          marker.frameNoLongerOnStack();
          throw e;
        } else {
          result = e.result();
        }
      }

      marker.frameNoLongerOnStack();
      return result;
    }
  }
}
