/**
 * Copyright (c) 2009 Michael Haupt, michael.haupt@hpi.uni-potsdam.de
 * Software Architecture Group, Hasso Plattner Institute, Potsdam, Germany
 * http://www.hpi.uni-potsdam.de/swa/
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

package som.vmobjects;

/**
 * Frame layout:
 * 
 * +-----------------+
 * | Arguments       | 0
 * +-----------------+
 * | Local Variables | <-- localOffset
 * +-----------------+
 * | Stack           | <-- stackPointer
 * | ...             |
 * +-----------------+
 */

/** REMOVED FOR TRUFFLE
public class Frame extends Array {

  public Frame(final Object nilObject) {
    super(nilObject);
  }

  public Frame getPreviousFrame() {
    // Get the previous frame by reading the field with previous frame index
    return (Frame) getField(previousFrameIndex);
  }

  public void setPreviousFrame(Frame value) {
    // Set the previous frame by writing to the field with previous frame
    // index
    setField(previousFrameIndex, value);
  }

  public void clearPreviousFrame(Object nilObject) {
    // Set the previous frame to nil
    setField(previousFrameIndex, nilObject);
  }

  public boolean hasPreviousFrame(Object nilObject) {
    return getField(previousFrameIndex) != nilObject;
  }

  public boolean isBootstrapFrame(Object nilObject) {
    return !hasPreviousFrame(nilObject);
  }

  public Frame getContext() {
    // Get the context by reading the field with context index
    return (Frame) getField(contextIndex);
  }

  public void setContext(Frame value) {
    // Set the context by writing to the field with context index
    setField(contextIndex, value);
  }

  public boolean hasContext(Object nilObject) {
    return getField(contextIndex) != nilObject;
  }

  public Frame getContext(int level) {
    // Get the context frame at the given level
    Frame frame = this;

    // Iterate through the context chain until the given level is reached
    while (level > 0) {
      // Get the context of the current frame
      frame = frame.getContext();

      // Go to the next level
      level = level - 1;
    }

    // Return the found context
    return frame;
  }

  public Frame getOuterContext(Object nilObject) {
    // Compute the outer context of this frame
    Frame frame = this;

    // Iterate through the context chain until null is reached
    while (frame.hasContext(nilObject))
      frame = frame.getContext();

    // Return the outer context
    return frame;
  }

  public Method getMethod() {
    // Get the method by reading the field with method index
    return (Method) getField(methodIndex);
  }

  public void setMethod(Method value) {
    // Set the method by writing to the field with method index
    setField(methodIndex, value);
  }

  public int getDefaultNumberOfFields() {
    // Return the default number of fields in a frame
    return numberOfFrameFields;
  }

  public Object pop() {
    // Pop an object from the expression stack and return it
    int stackPointer = getStackPointer();
    setStackPointer(stackPointer - 1);
    return getIndexableField(stackPointer);
  }

  public void push(Object value) {
    // Push an object onto the expression stack
    int stackPointer = getStackPointer() + 1;
    setIndexableField(stackPointer, value);
    setStackPointer(stackPointer);
  }

  public int getStackPointer() {
    // Get the current stack pointer for this frame
    return stackPointer;
  }

  public void setStackPointer(int value) {
    // Set the current stack pointer for this frame
    stackPointer = value;
  }

  public void resetStackPointer() {
    // arguments are stored in front of local variables
    localOffset = getMethod().getNumberOfArguments();

    // Set the stack pointer to its initial value thereby clearing the stack
    setStackPointer(localOffset
        + getMethod().getNumberOfLocals().getEmbeddedInteger() - 1);
  }

  public int getBytecodeIndex() {
    // Get the current bytecode index for this frame
    return bytecodeIndex;
  }

  public void setBytecodeIndex(int value) {
    // Set the current bytecode index for this frame
    bytecodeIndex = value;
  }

  public Object getStackElement(int index) {
    // Get the stack element with the given index
    // (an index of zero yields the top element)
    return getIndexableField(getStackPointer() - index);
  }

  public void setStackElement(int index, Object value) {
    // Set the stack element with the given index to the given value
    // (an index of zero yields the top element)
    setIndexableField(getStackPointer() - index, value);
  }

  private Object getLocal(int index) {
    return getIndexableField(localOffset + index);
  }

  private void setLocal(int index, Object value) {
    setIndexableField(localOffset + index, value);
  }

  public Object getLocal(int index, int contextLevel) {
    // Get the local with the given index in the given context
    return getContext(contextLevel).getLocal(index);
  }

  public void setLocal(int index, int contextLevel, Object value) {
    // Set the local with the given index in the given context to the given
    // value
    getContext(contextLevel).setLocal(index, value);
  }

  public Object getArgument(int index, int contextLevel) {
    // Get the context
    Frame context = getContext(contextLevel);

    // Get the argument with the given index
    return context.getIndexableField(index);
  }

  public void setArgument(int index, int contextLevel, Object value) {
    // Get the context
    Frame context = getContext(contextLevel);

    // Set the argument with the given index to the given value
    context.setIndexableField(index, value);
  }

  public void copyArgumentsFrom(Frame frame) {
    // copy arguments from frame:
    // - arguments are at the top of the stack of frame.
    // - copy them into the argument area of the current frame
    int numArgs = getMethod().getNumberOfArguments();
    for (int i = 0; i < numArgs; ++i) {
      setIndexableField(i, frame.getStackElement(numArgs - 1 - i));
    }
  }

  public void printStackTrace(Object nilObject) {
    // Print a stack trace starting in this frame
    System.out.print(getMethod().getHolder().getName().getString());
    System.out.print(getBytecodeIndex() + "@"
        + getMethod().getSignature().getString());
    if (hasPreviousFrame(nilObject))
      getPreviousFrame().printStackTrace(nilObject);
  }

  // Private variables holding the stack pointer and the bytecode index
  private int      stackPointer;
  private int      bytecodeIndex;

  // the offset at which local variables start
  private int      localOffset;

  // Static field indices and number of frame fields
  static final int previousFrameIndex  = 1 + classIndex;
  static final int contextIndex        = 1 + previousFrameIndex;
  static final int methodIndex         = 1 + contextIndex;
  static final int numberOfFrameFields = 1 + methodIndex;
}
*/
