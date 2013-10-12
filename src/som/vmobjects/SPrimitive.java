///**
// * Copyright (c) 2013 Stefan Marr,   stefan.marr@vub.ac.be
// * Copyright (c) 2009 Michael Haupt, michael.haupt@hpi.uni-potsdam.de
// * Software Architecture Group, Hasso Plattner Institute, Potsdam, Germany
// * http://www.hpi.uni-potsdam.de/swa/
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy
// * of this software and associated documentation files (the "Software"), to deal
// * in the Software without restriction, including without limitation the rights
// * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// * copies of the Software, and to permit persons to whom the Software is
// * furnished to do so, subject to the following conditions:
// *
// * The above copyright notice and this permission notice shall be included in
// * all copies or substantial portions of the Software.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// * THE SOFTWARE.
// */
//
//package som.vmobjects;
//
//import som.interpreter.Method;
//import som.vm.Universe;
//
//import com.oracle.truffle.api.CallTarget;
//import com.oracle.truffle.api.frame.PackedFrame;
//
//public abstract class SPrimitive extends SObject implements SInvokable {
//
//  @Override
//  public boolean isPrimitive() {
//    return true;
//  }
//
//  @Override
//  public CallTarget getCallTarget() {
//    return null;
//  }
//
//  @Override
//  public Method getTruffleInvokable() {
//    return null;
//  }
//
//  public SPrimitive(final java.lang.String signatureString, final Universe universe) {
//    super(universe.nilObject);
//
//    // Set the class of this primitive to be the universal primitive class
//    setClass(universe.primitiveClass);
//
//    // Set the signature of this primitive
//    setSignature(universe.symbolFor(signatureString));
//  }
//
//  @Override
//  public SSymbol getSignature() {
//    // Get the signature by reading the field with signature index
//    return (SSymbol) getField(signatureIndex);
//  }
//
//  public void setSignature(final SSymbol value) {
//    // Set the signature by writing to the field with signature index
//    setField(signatureIndex, value);
//  }
//
//  @Override
//  public SClass getHolder() {
//    // Get the holder of this method by reading the field with holder index
//    return (SClass) getField(holderIndex);
//  }
//
//  @Override
//  public void setHolder(final SClass value) {
//    // Set the holder of this method by writing to the field with holder
//    // index
//    setField(holderIndex, value);
//  }
//
//  @Override
//  public int getDefaultNumberOfFields() {
//    // Return the default number of fields for a primitive
//    return numberOfPrimitiveFields;
//  }
//
//  public boolean isEmpty() {
//    // By default a primitive is not empty
//    return false;
//  }
//
//  public static SPrimitive getEmptyPrimitive(final java.lang.String signatureString,
//      final Universe universe) {
//    // Return an empty primitive with the given signature
//    return (new SPrimitive(signatureString, universe) {
//
//      @Override
//      public SObject invoke(final PackedFrame frame,
//          final SObject self,
//          final SObject[] args) {
//        // Write a warning to the screen
//        Universe.println("Warning: undefined primitive "
//            + this.getSignature().getString() + " called");
//        return null;
//      }
//
//      @Override
//      public boolean isEmpty() {
//        // The empty primitives are empty
//        return true;
//      }
//    });
//  }
//
//  // Static field indices and number of primitive fields
//  static final int signatureIndex          = numberOfObjectFields;
//  static final int holderIndex             = 1 + signatureIndex;
//  static final int numberOfPrimitiveFields = 1 + holderIndex;
//}
//
