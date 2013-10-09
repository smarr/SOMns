/**
 * Copyright (c) 2013 Stefan Marr,   stefan.marr@vub.ac.be
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

import som.interpreter.Arguments;
import som.interpreter.Method;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.PackedFrame;

public class SMethod extends SObject implements SInvokable {

  public SMethod(final SObject nilObject,
      final SSymbol signature,
      final som.interpreter.Method truffleInvokable,
      final FrameDescriptor frameDescriptor) {
    super(nilObject);
    setSignature(signature);

    this.truffleInvokable = truffleInvokable; // TODO: remove truffleInvokable if possible/useful

    TruffleRuntime runtime =  Truffle.getRuntime(); // TODO: should be: universe.getTruffleRuntime();

    CallTarget target = runtime.createCallTarget(truffleInvokable, frameDescriptor);
    this.callTarget = target;
  }

  @Override
  public CallTarget getCallTarget() {
    return callTarget;
  }

  @Override
  public Method getTruffleInvokable() {
    return truffleInvokable;
  }

  @Override
  public boolean isPrimitive() {
    return false;
  }

  @Override
  public SSymbol getSignature() {
    // Get the signature of this method by reading the field with signature
    // index
    return (SSymbol) getField(signatureIndex);
  }

  private void setSignature(final SSymbol value) {
    // Set the signature of this method by writing to the field with
    // signature index
    setField(signatureIndex, value);
  }

  @Override
  public SClass getHolder() {
    // Get the holder of this method by reading the field with holder index
    return (SClass) getField(holderIndex);
  }

  @Override
  public void setHolder(final SClass value) {
    // Set the holder of this method by writing to the field with holder index
    setField(holderIndex, value);
  }

  public int getNumberOfArguments() {
    // Get the number of arguments of this method
    return getSignature().getNumberOfSignatureArguments();
  }

  @Override
  public int getDefaultNumberOfFields() {
    // Return the default number of fields in a method
    return numberOfMethodFields;
  }

  public SObject invokeRoot(final SObject self, final SObject[] args) {
    SObject result = (SObject) callTarget.call(new Arguments(self, args));
    return result;
  }

  @Override
  public SObject invoke(final PackedFrame caller,
      final SObject self,
      final SObject[] args) {
    SObject result = (SObject) callTarget.call(caller, new Arguments(self, args));
    return result;
  }

  @Override
  public java.lang.String toString() {
    // TODO: fixme: remove special case if possible, I think it indicates a bug
    if (!(getField(holderIndex) instanceof SClass)) {
      return "Method(nil>>" + getSignature().toString() + ")";
    }

    return "Method(" + getHolder().getName().getString() + ">>" + getSignature().toString() + ")";
  }

  // Private variable holding Truffle runtime information
  private final som.interpreter.Method truffleInvokable;
  private final CallTarget             callTarget;

  // Static field indices and number of method fields
  static final int                     signatureIndex       = numberOfObjectFields;
  static final int                     holderIndex          = 1 + signatureIndex;
  static final int                     numberOfMethodFields = 1 + holderIndex;
}
