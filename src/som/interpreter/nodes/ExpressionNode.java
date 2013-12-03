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

import java.math.BigInteger;

import som.interpreter.TypesGen;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SBigInteger;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SString;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public abstract class ExpressionNode extends SOMNode {

  public abstract Object executeGeneric(final VirtualFrame frame);

  public int executeInteger(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.TYPES.expectInteger(executeGeneric(frame));
  }

  public BigInteger executeBigInteger(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.TYPES.expectBigInteger(executeGeneric(frame));
  }

  public String executeString(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.TYPES.expectString(executeGeneric(frame));
  }

  public boolean executeBoolean(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.TYPES.expectBoolean(executeGeneric(frame));
  }

  public double executeDouble(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.TYPES.expectDouble(executeGeneric(frame));
  }

  public SInteger executeSInteger(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.TYPES.expectSInteger(executeGeneric(frame));
  }

  public SBigInteger executeSBigInteger(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.TYPES.expectSBigInteger(executeGeneric(frame));
  }

  public SDouble executeSDouble(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.TYPES.expectSDouble(executeGeneric(frame));
  }

  public SArray executeSArray(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.TYPES.expectSArray(executeGeneric(frame));
  }

  public SString executeSString(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.TYPES.expectSString(executeGeneric(frame));
  }

  public SSymbol executeSSymbol(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.TYPES.expectSSymbol(executeGeneric(frame));
  }

  public SBlock executeSBlock(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.TYPES.expectSBlock(executeGeneric(frame));
  }

  public SClass executeSClass(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.TYPES.expectSClass(executeGeneric(frame));
  }

  public SMethod executeSMethod(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.TYPES.expectSMethod(executeGeneric(frame));
  }

  public SObject executeSObject(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.TYPES.expectSObject(executeGeneric(frame));
  }

  public SAbstractObject executeSAbstractObject(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.TYPES.expectSAbstractObject(executeGeneric(frame));
  }

  public Object[] executeArray(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.TYPES.expectObjectArray(executeGeneric(frame));
  }

  public Object executeObject(final VirtualFrame frame) throws UnexpectedResultException {
    return executeGeneric(frame);
  }


////  @SuppressWarnings("unused")
//  public SOMObject executeEvaluated(VirtualFrame frame, java.lang.Object val1) {
//      return executeGeneric(frame);
//  }
//
////  @SuppressWarnings("unused")
//  public SOMObject executeEvaluated(VirtualFrame frame, java.lang.Object val1, java.lang.Object val2) {
//      return executeEvaluated(frame, val1);
//  }
//
////  @SuppressWarnings("unused")
//  public SOMObject executeEvaluated(VirtualFrame frame, java.lang.Object val1, java.lang.Object val2, java.lang.Object val3) {
//      return executeEvaluated(frame, val1, val2);
//  }
}
