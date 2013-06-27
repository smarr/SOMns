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

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;


public class Method extends RootNode {

  @Child private final SequenceNode expressions;

  private final FrameSlot   selfSlot;
  private final FrameSlot[] argumentSlots;

  public Method(final SequenceNode expressions,
                  final FrameSlot selfSlot,
                  final FrameSlot[] argumentSlots) {
    this.expressions   = expressions;
    this.selfSlot      = selfSlot;
    this.argumentSlots = argumentSlots;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    initializeFrame(frame);

    Object result;
    try {
        result = expressions.executeGeneric(frame);
    } catch (ReturnException e) {
      if (!e.reachedTarget(frame)) {
        throw e;
      } else {
        result = e.result();
      }
    }

    return result;
  }

  private void initializeFrame(VirtualFrame frame) {
    Object[] args = frame.getArguments(Arguments.class).arguments;
    try {
      for (int i = 0; i < argumentSlots.length; i++) {
        frame.setObject(argumentSlots[i], args[i]);
      }

      frame.setObject(selfSlot, frame.getArguments(Arguments.class).self);
    } catch (FrameSlotTypeException e) {
     throw new RuntimeException("Should not happen, since we only have one type currently!");
    }
  }
}
