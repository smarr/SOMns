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

package som.primitives;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.vm.Universe;
import som.vmobjects.BigInteger;
import som.vmobjects.Double;
import som.vmobjects.Integer;
import som.vmobjects.Object;
import som.vmobjects.Primitive;

public class IntegerPrimitives extends Primitives {

  public IntegerPrimitives(final Universe universe) {
    super(universe);
  }

  private Object  makeInt(long result) {
    // Check with integer bounds and push:
    if (result > java.lang.Integer.MAX_VALUE
        || result < java.lang.Integer.MIN_VALUE)
      return universe.newBigInteger(result);
    else
      return universe.newInteger((int) result);
  }

  private Object resendAsBigInteger(java.lang.String operator, Integer left,
      BigInteger right, final VirtualFrame frame) {
    // Construct left value as BigInteger:
    BigInteger leftBigInteger = universe.newBigInteger((long) left
        .getEmbeddedInteger());

    // Resend message:
    Object[] operands = new Object[1];
    operands[0] = right;

    return leftBigInteger
        .send(operator, operands, universe, frame);
  }

  private Object resendAsDouble(java.lang.String operator, Integer left, Double right,
      final VirtualFrame frame) {
    Double leftDouble = universe.newDouble((double) left.getEmbeddedInteger());
    Object[] operands = new Object[] { right };
    return leftDouble.send(operator, operands, universe, frame);
  }

  public void installPrimitives() {
    installInstancePrimitive(new Primitive("asString", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Integer self = (Integer) selfO;
        return universe.newString(java.lang.Integer.toString(self
            .getEmbeddedInteger()));
      }
    });

    installInstancePrimitive(new Primitive("sqrt", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Integer self = (Integer) selfO;
        return universe.newDouble(Math.sqrt((double) self
            .getEmbeddedInteger()));
      }
    });

    installInstancePrimitive(new Primitive("atRandom", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Integer self = (Integer) selfO;
        return universe.newInteger((int) ((double) self
            .getEmbeddedInteger() * Math.random()));
      }
    });

    installInstancePrimitive(new Primitive("+", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Object rightObj = args[0];
        Integer left = (Integer) selfO;

        // Check second parameter type:
        if (rightObj instanceof BigInteger) {
          // Second operand was BigInteger
          return resendAsBigInteger("+", left, (BigInteger) rightObj, frame);
        }
        else if (rightObj instanceof Double) {
          return resendAsDouble("+", left, (Double) rightObj, frame);
        }
        else {
          // Do operation:
          Integer right = (Integer) rightObj;

          long result = ((long) left.getEmbeddedInteger())
              + right.getEmbeddedInteger();
          return makeInt(result);
        }
      }
    });

    installInstancePrimitive(new Primitive("-", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Object rightObj = args[0];
        Integer left = (Integer) selfO;

        // Check second parameter type:
        if (rightObj instanceof BigInteger) {
          // Second operand was BigInteger
          return resendAsBigInteger("-", left, (BigInteger) rightObj, frame);
        }
        else if (rightObj instanceof Double) {
          return resendAsDouble("-", left, (Double) rightObj, frame);
        }
        else {
          // Do operation:
          Integer right = (Integer) rightObj;

          long result = ((long) left.getEmbeddedInteger())
              - right.getEmbeddedInteger();
          return makeInt(result);
        }
      }
    });

    installInstancePrimitive(new Primitive("*", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Object rightObj = args[0];
        Integer left = (Integer) selfO;

        // Check second parameter type:
        if (rightObj instanceof BigInteger) {
          // Second operand was BigInteger
          return resendAsBigInteger("*", left, (BigInteger) rightObj, frame);
        }
        else if (rightObj instanceof Double) {
          return resendAsDouble("*", left, (Double) rightObj, frame);
        }
        else {
          // Do operation:
          Integer right = (Integer) rightObj;

          long result = ((long) left.getEmbeddedInteger())
              * right.getEmbeddedInteger();
          return makeInt(result);
        }
      }
    });

    installInstancePrimitive(new Primitive("//", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        /*
         * Integer op1 = (Integer) frame.pop(); Integer op2 = (Integer)
         * frame.pop();
         * frame.push(universe.new_double((double)op2.get_embedded_integer () /
         * (double)op1.get_embedded_integer()));
         */
        Object rightObj = args[0];
        Integer left = (Integer) selfO;

        // Check second parameter type:
        if (rightObj instanceof BigInteger) {
          // Second operand was BigInteger
          return resendAsBigInteger("/", left, (BigInteger) rightObj, frame);
        }
        else if (rightObj instanceof Double) {
          return resendAsDouble("/", left, (Double) rightObj, frame);
        }
        else {
          // Do operation:
          Integer right = (Integer) rightObj;

          double result = ((double) left.getEmbeddedInteger())
              / right.getEmbeddedInteger();
          return universe.newDouble(result);
        }
      }
    });

    installInstancePrimitive(new Primitive("/", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Object rightObj = args[0];
        Integer left = (Integer) selfO;

        // Check second parameter type:
        if (rightObj instanceof BigInteger) {
          // Second operand was BigInteger
          return resendAsBigInteger("/", left, (BigInteger) rightObj, frame);
        }
        else if (rightObj instanceof Double) {
          return resendAsDouble("/", left, (Double) rightObj, frame);
        }
        else {
          // Do operation:
          Integer right = (Integer) rightObj;

          long result = ((long) left.getEmbeddedInteger())
              / right.getEmbeddedInteger();
          return makeInt(result);
        }
      }
    });

    installInstancePrimitive(new Primitive("%", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Object rightObj = args[0];
        Integer left = (Integer) selfO;

        // Check second parameter type:
        if (rightObj instanceof BigInteger) {
          // Second operand was BigInteger
          return resendAsBigInteger("%", left, (BigInteger) rightObj, frame);
        }
        else if (rightObj instanceof Double) {
          return resendAsDouble("%", left, (Double) rightObj, frame);
        }
        else {
          // Do operation:
          Integer right = (Integer) rightObj;

          long l = (long) left.getEmbeddedInteger();
          long r = (long) right.getEmbeddedInteger();
          long result = l % r;

          if (l > 0 && r < 0) {
            result += r;
          }

          return makeInt(result);
        }
      }
    });

    installInstancePrimitive(new Primitive("&", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Object rightObj = args[0];
        Integer left = (Integer) selfO;

        // Check second parameter type:
        if (rightObj instanceof BigInteger) {
          // Second operand was BigInteger
          return resendAsBigInteger("&", left, (BigInteger) rightObj, frame);
        }
        else if (rightObj instanceof Double) {
          return resendAsDouble("&", left, (Double) rightObj, frame);
        }
        else {
          // Do operation:
          Integer right = (Integer) rightObj;

          long result = ((long) left.getEmbeddedInteger())
              & right.getEmbeddedInteger();
          return makeInt(result);
        }
      }
    });

    installInstancePrimitive(new Primitive("=", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Object rightObj = args[0];
        Integer left = (Integer) selfO;

        // Check second parameter type:
        if (rightObj instanceof BigInteger) {
          // Second operand was BigInteger:
          return resendAsBigInteger("=", left, (BigInteger) rightObj, frame);
        }
        else if (rightObj instanceof Integer) {
          // Second operand was Integer:
          Integer right = (Integer) rightObj;

          if (left.getEmbeddedInteger() == right.getEmbeddedInteger())
            return universe.trueObject;
          else
            return universe.falseObject;
        }
        else if (rightObj instanceof Double) {
          // Second operand was Integer:
          Double right = (Double) rightObj;

          if (left.getEmbeddedInteger() == right.getEmbeddedDouble())
            return universe.trueObject;
          else
            return universe.falseObject;
        }
        else
          return universe.falseObject;
      }
    });

    installInstancePrimitive(new Primitive("<", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Object rightObj = args[0];
        Integer left = (Integer) selfO;

        // Check second parameter type:
        if (rightObj instanceof BigInteger) {
          // Second operand was BigInteger
          return resendAsBigInteger("<", left, (BigInteger) rightObj, frame);
        }
        else if (rightObj instanceof Double) {
          return resendAsDouble("<", left, (Double) rightObj, frame);
        }
        else {
          // Do operation:
          Integer right = (Integer) rightObj;

          if (left.getEmbeddedInteger() < right.getEmbeddedInteger())
            return universe.trueObject;
          else
            return universe.falseObject;
        }
      }
    });
  }
}
