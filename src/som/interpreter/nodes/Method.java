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

import som.vm.Universe;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;


public class Method extends RootNode {

  @Child private final ExpressionNode expressionOrSequence;

  private final FrameSlot   selfSlot;
  @CompilationFinal private final FrameSlot[]  argumentSlots;
  @CompilationFinal private final FrameSlot[] temporarySlots;
  private final FrameSlot   nonLocalReturnMarker;
  private final Universe    universe;

  private final FrameDescriptor frameDescriptor;

  public Method(final ExpressionNode expressions,
                final FrameSlot selfSlot,
                final FrameSlot[] argumentSlots,
                final FrameSlot[] temporarySlots,
                final FrameSlot nonLocalReturnMarker,
                final Universe  universe,
                final FrameDescriptor frameDescriptor) {
    this.expressionOrSequence = adoptChild(expressions);
    this.selfSlot        = selfSlot;
    this.argumentSlots   = argumentSlots;
    this.temporarySlots  = temporarySlots;
    this.nonLocalReturnMarker = nonLocalReturnMarker;
    this.universe        = universe;
    this.frameDescriptor = frameDescriptor;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    final FrameOnStackMarker marker = initializeFrame(this, frame.materialize());
    return messageSendExecution(marker, frame, expressionOrSequence);
  }

  public static som.vmobjects.Object messageSendExecution(final FrameOnStackMarker marker,
      final VirtualFrame frame,
      final ExpressionNode expr) {
    som.vmobjects.Object  result;
    boolean restart;

    do {
      restart = false;
      try {
        result = expr.executeGeneric(frame);
      } catch (ReturnException e) {
        if (!e.reachedTarget(marker)) {
          marker.frameNoLongerOnStack();
          throw e;
        } else {
          result = e.result();
        }
      } catch (RestartLoopException e) {
        restart = true;
        result  = null;
      }
    } while (restart);

    marker.frameNoLongerOnStack();
    return result;
  }

  @ExplodeLoop
  public static FrameOnStackMarker initializeFrame(final Method method,
      final MaterializedFrame frame) {
    try {
      frame.setObject(method.selfSlot, frame.getArguments(Arguments.class).self);

      final FrameOnStackMarker marker = new FrameOnStackMarker();
      frame.setObject(method.nonLocalReturnMarker, marker);

      Object[] args = frame.getArguments(Arguments.class).arguments;
      for (int i = 0; i < method.argumentSlots.length; i++) {
        frame.setObject(method.argumentSlots[i], args[i]);
      }

      for (int i = 0; i < method.temporarySlots.length; i++) {
        frame.setObject(method.temporarySlots[i], method.universe.nilObject);
      }

      return marker;
    } catch (FrameSlotTypeException e) {
      throw new RuntimeException("Should not happen, since we only have one type currently!");
    }
  }

  @Override
  public String toString() {
    SourceSection ss = getSourceSection();
    final String name = ss.getIdentifier();
    final String location = getSourceSection().toString();
    return "Method " + name + ":" + location + "@" + Integer.toHexString(hashCode());
  }

  public FrameDescriptor getFrameDescriptor() {
    return frameDescriptor;
  }

  public ExpressionNode methodCloneForInlining() {
    return expressionOrSequence.cloneForInlining();
  }
}
