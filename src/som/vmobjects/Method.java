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

import som.interpreter.nodes.Arguments;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.PackedFrame;

public class Method extends Array implements Invokable {

  public Method(final Object nilObject,
      final som.interpreter.nodes.Method truffleInvokable,
      final FrameDescriptor frameDescriptor) {
    super(nilObject);
    this.truffleInvokable = truffleInvokable;

    TruffleRuntime runtime =  Truffle.getRuntime(); // TODO: should be: universe.getTruffleRuntime();

    CallTarget target = runtime.createCallTarget(truffleInvokable, frameDescriptor);
    this.callTarget = target;
  }

  public som.interpreter.nodes.Method getTruffleInvokable() {
    return truffleInvokable;
  }

  public boolean isPrimitive() {
    return false;
  }

  public Symbol getSignature() {
    // Get the signature of this method by reading the field with signature
    // index
    return (Symbol) getField(signatureIndex);
  }

  public void setSignature(Symbol value) {
    // Set the signature of this method by writing to the field with
    // signature index
    setField(signatureIndex, value);
  }

  public Class getHolder() {
    // Get the holder of this method by reading the field with holder index
    return (Class) getField(holderIndex);
  }

  public void setHolder(Class value) {
    // Set the holder of this method by writing to the field with holder index
    setField(holderIndex, value);

    // Make sure all nested invokables have the same holder
    for (int i = 0; i < getNumberOfIndexableFields(); i++) {
      if (getIndexableField(i) instanceof Invokable) {
        ((Invokable) getIndexableField(i)).setHolder(value);
      }
    }
  }

  public int getNumberOfArguments() {
    // Get the number of arguments of this method
    return getSignature().getNumberOfSignatureArguments();
  }

  public int getDefaultNumberOfFields() {
    // Return the default number of fields in a method
    return numberOfMethodFields;
  }

  public void increaseInvocationCounter() {
    invocationCount++;
  }

  public long getInvocationCount() {
    return invocationCount;
  }

  public Object invokeRoot(final Object self, final Object[] args) {
    // Increase the invocation counter
    invocationCount++;

    Object result = (Object) callTarget.call(new Arguments(self, args));

    return result;
  }

  public Object invoke(final PackedFrame frame,
      final Object self,
      final Object[] args) {
    // Increase the invocation counter
    invocationCount++;

    Object result = (Object) callTarget.call(frame, new Arguments(self, args));

    return result;
  }

  public Class getReceiverClass(byte index) {
    return receiverClassTable.get(index);
  }

  public Invokable getInvokedMethod(byte index) {
    // return the last invoked method for a particular send
    return invokedMethods.get(index);
  }

  public byte addReceiverClassAndMethod(Class recClass, Invokable invokable) {
    receiverClassTable.add(receiverClassIndex, recClass);
    invokedMethods.add(receiverClassIndex, invokable);
    receiverClassIndex++;

    return (byte) (receiverClassIndex - 1);
  }

  public boolean isReceiverClassTableFull() {
    return receiverClassIndex == 255;
  }

  @Override
  public java.lang.String toString() {
    return "Method(" + getHolder().getName().getString() + ">>" + getSignature().toString() + ")";
  }

  // Private variables for holding the last receiver class and invoked method
  private java.util.ArrayList<Class>     receiverClassTable                = new java.util.ArrayList<Class>();
  private java.util.ArrayList<Invokable> invokedMethods                    = new java.util.ArrayList<Invokable>();
  private int                            receiverClassIndex                = 0;

  // Private variable holding number of invocations and back-edges
  private long                           invocationCount;

  // Private variable holding Truffle runtime information
  private final som.interpreter.nodes.Method truffleInvokable;
  private final CallTarget callTarget;

  // Static field indices and number of method fields
  static final int                       signatureIndex                    = 1 + classIndex;
  static final int                       holderIndex                       = 1 + signatureIndex;
  static final int                       numberOfMethodFields              = 1 + holderIndex;

}
