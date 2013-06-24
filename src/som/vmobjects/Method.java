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

import som.interpreter.nodes.Arguments;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;

public class Method extends Array implements Invokable {

  public Method(final Object nilObject,
      final som.interpreter.nodes.Method truffleInvokable,
      final FrameDescriptor frameDescriptor) {
    super(nilObject);
    this.truffleInvokable = truffleInvokable;
    this.frameDescriptor  = frameDescriptor;
  }
  
  public som.interpreter.nodes.Method getTruffleInvokable() {
    return truffleInvokable;
  }
  
  public boolean isPrimitive() {
    return false;
  }

  /** REMOVED FOR TRUFFLE
  public Integer getNumberOfLocals() {
    // Get the number of locals (converted to a Java integer)
    return (Integer) getField(numberOfLocalsIndex);
  }

  public void setNumberOfLocals(Integer value) {
    // Set the number of locals
    setField(numberOfLocalsIndex, value);
  }

  public Integer getMaximumNumberOfStackElements() {
    // Get the maximum number of stack elements (converted to a Java
    // integer)
    return (Integer) getField(maximumNumberOfStackElementsIndex);
  }

  public void setMaximumNumberOfStackElements(Integer value) {
    // Set the maximum number of stack elements
    setField(maximumNumberOfStackElementsIndex, value);
  } */

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
    // Set the holder of this method by writing to the field with holder
    // index
    setField(holderIndex, value);

    // Make sure all nested invokables have the same holder
    for (int i = 0; i < getNumberOfIndexableFields(); i++)
      if (getIndexableField(i) instanceof Invokable)
        ((Invokable) getIndexableField(i)).setHolder(value);
  }

  /** REMOVED FOR TRUFFLE
  public Object getConstant(int bytecodeIndex) {
    // Get the constant associated to a given bytecode index
    return getIndexableField(getBytecode(bytecodeIndex + 1));
  } */

  public int getNumberOfArguments() {
    // Get the number of arguments of this method
    return getSignature().getNumberOfSignatureArguments();
  }

  public int getDefaultNumberOfFields() {
    // Return the default number of fields in a method
    return numberOfMethodFields;
  }

  /** REMOVED FOR TRUFFLE
  public int getNumberOfBytecodes() {
    // Get the number of bytecodes in this method
    return bytecodes.length;
  }

  public void setNumberOfBytecodes(int value) {
    // Set the number of bytecodes in this method
    bytecodes = new byte[value];
  }

  public byte getBytecode(int index) {
    // Get the bytecode at the given index
    return bytecodes[index];
  }

  public void setBytecode(int index, byte value) {
    // Set the bytecode at the given index to the given value
    bytecodes[index] = value;
  } */

  public void increaseInvocationCounter() {
    invocationCount++;
  }

  public long getInvocationCount() {
    return invocationCount;
  }

  public Object invokeRoot(final Object self, final Object[] args) {
    // Increase the invocation counter
    invocationCount++;
    
    TruffleRuntime runtime =  Truffle.getRuntime(); // TODO: should be: universe.getTruffleRuntime();
    
    CallTarget target = runtime.createCallTarget(truffleInvokable, frameDescriptor);
    Object result = (Object) target.call(new Arguments(self, args));
    
    return result;
  }
  
  public Object invoke(final VirtualFrame frame,
      final Object self,
      final Object[] args) {
    // Increase the invocation counter
    invocationCount++;

    TruffleRuntime runtime =  Truffle.getRuntime(); // TODO: should be: universe.getTruffleRuntime();
    
    CallTarget target = runtime.createCallTarget(truffleInvokable, frameDescriptor);
    Object result = (Object) target.call(frame.pack(), new Arguments(self, args));
    
    return result;
  }

  /** REMOVED FOR TRUFFLE
  public void replaceBytecodes() {
    byte newbc[] = new byte[bytecodes.length];
    int idx = 0;

    for (int i = 0; i < bytecodes.length;) {
      byte bc1 = bytecodes[i];
      int len1 = Bytecodes.getBytecodeLength(bc1);

      if (i + len1 >= bytecodes.length) {
        // we're over target, so just copy bc1
        for (int j = i; j < i + len1; ++j) {
          newbc[idx++] = bytecodes[j];
        }
        break;
      }

      newbc[idx++] = bc1;

      // copy args to bc1
      for (int j = i + 1; j < i + len1; ++j) {
        newbc[idx++] = bytecodes[j];
      }

      i += len1; // update i to point on bc2

    }

    // we copy the new array because it may be shorter, and we don't
    // want to upset whatever dependence there is on the length
    bytecodes = new byte[idx];
    for (int i = 0; i < idx; ++i) {
      bytecodes[i] = newbc[i];
    }
  } */

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

  // Private variables for holding the last receiver class and invoked method
  private java.util.ArrayList<Class>     receiverClassTable                = new java.util.ArrayList<Class>();
  private java.util.ArrayList<Invokable> invokedMethods                    = new java.util.ArrayList<Invokable>();
  private int                            receiverClassIndex                = 0;

  // Private variable holding number of invocations and backedges
  private long                           invocationCount;
  
  // Private variable holding Truffle Invokable
  final private som.interpreter.nodes.Method truffleInvokable;
  final private FrameDescriptor              frameDescriptor;

  // Static field indices and number of method fields
  static final int                       signatureIndex                    = 1 + classIndex;
  static final int                       holderIndex                       = 1 + signatureIndex;
  static final int                       numberOfMethodFields              = 1 + holderIndex;
 
}
