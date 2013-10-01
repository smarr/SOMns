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

import com.oracle.truffle.api.frame.PackedFrame;
import som.vm.Universe;
import som.vmobjects.SBigInteger;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SObject;
import som.vmobjects.SPrimitive;
import som.vmobjects.SString;

public class IntegerPrimitives extends Primitives {

  public IntegerPrimitives(final Universe universe) {
    super(universe);
  }

  private SObject  makeInt(long result) {
    // Check with integer bounds and push:
    if (result > java.lang.Integer.MAX_VALUE
        || result < java.lang.Integer.MIN_VALUE) {
      return universe.newBigInteger(result);
    } else {
      return universe.newInteger((int) result);
    }
  }

  private SObject resendAsBigInteger(java.lang.String operator, SInteger left,
      SBigInteger right, final PackedFrame frame) {
    // Construct left value as BigInteger:
    SBigInteger leftBigInteger = universe.
        newBigInteger((long) left.getEmbeddedInteger());

    // Resend message:
    SObject[] operands = new SObject[1];
    operands[0] = right;

    return leftBigInteger.send(operator, operands, universe, frame);
  }

  private SObject resendAsDouble(java.lang.String operator, SInteger left, SDouble right,
      final PackedFrame frame) {
    SDouble leftDouble = universe.newDouble((double) left.getEmbeddedInteger());
    SObject[] operands = new SObject[] {right};
    return leftDouble.send(operator, operands, universe, frame);
  }

  public void installPrimitives() {
    installInstancePrimitive(new SPrimitive("asString", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SInteger self = (SInteger) selfO;
        return universe.newString(java.lang.Integer.toString(
            self.getEmbeddedInteger()));
      }
    });

    installInstancePrimitive(new SPrimitive("sqrt", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SInteger self = (SInteger) selfO;

        double result = Math.sqrt((double) self.getEmbeddedInteger());

        if (result == Math.rint(result)) {
          return makeInt((long) result);
        } else {
          return universe.newDouble(result);
        }
      }
    });

    installInstancePrimitive(new SPrimitive("atRandom", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SInteger self = (SInteger) selfO;
        return universe.newInteger(
            (int) ((double) self.getEmbeddedInteger() * Math.random()));
      }
    });

    installInstancePrimitive(new SPrimitive("+", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SObject rightObj = args[0];
        SInteger left = (SInteger) selfO;

        // Check second parameter type:
        if (rightObj instanceof SBigInteger) {
          // Second operand was BigInteger
          return resendAsBigInteger("+", left, (SBigInteger) rightObj, frame);
        } else if (rightObj instanceof SDouble) {
          return resendAsDouble("+", left, (SDouble) rightObj, frame);
        } else {
          // Do operation:
          SInteger right = (SInteger) rightObj;

          long result = ((long) left.getEmbeddedInteger())
              + right.getEmbeddedInteger();
          return makeInt(result);
        }
      }
    });

    installInstancePrimitive(new SPrimitive("-", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SObject rightObj = args[0];
        SInteger left = (SInteger) selfO;

        // Check second parameter type:
        if (rightObj instanceof SBigInteger) {
          // Second operand was BigInteger
          return resendAsBigInteger("-", left, (SBigInteger) rightObj, frame);
        } else if (rightObj instanceof SDouble) {
          return resendAsDouble("-", left, (SDouble) rightObj, frame);
        } else {
          // Do operation:
          SInteger right = (SInteger) rightObj;

          long result = ((long) left.getEmbeddedInteger())
              - right.getEmbeddedInteger();
          return makeInt(result);
        }
      }
    });

    installInstancePrimitive(new SPrimitive("*", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SObject rightObj = args[0];
        SInteger left = (SInteger) selfO;

        // Check second parameter type:
        if (rightObj instanceof SBigInteger) {
          // Second operand was BigInteger
          return resendAsBigInteger("*", left, (SBigInteger) rightObj, frame);
        } else if (rightObj instanceof SDouble) {
          return resendAsDouble("*", left, (SDouble) rightObj, frame);
        } else {
          // Do operation:
          SInteger right = (SInteger) rightObj;

          long result = ((long) left.getEmbeddedInteger())
              * right.getEmbeddedInteger();
          return makeInt(result);
        }
      }
    });

    installInstancePrimitive(new SPrimitive("//", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        /*
         * Integer op1 = (Integer) frame.pop(); Integer op2 = (Integer)
         * frame.pop();
         * frame.push(universe.new_double((double)op2.get_embedded_integer () /
         * (double)op1.get_embedded_integer()));
         */
        SObject rightObj = args[0];
        SInteger left = (SInteger) selfO;

        // Check second parameter type:
        if (rightObj instanceof SBigInteger) {
          // Second operand was BigInteger
          return resendAsBigInteger("/", left, (SBigInteger) rightObj, frame);
        } else if (rightObj instanceof SDouble) {
          return resendAsDouble("/", left, (SDouble) rightObj, frame);
        } else {
          // Do operation:
          SInteger right = (SInteger) rightObj;

          double result = ((double) left.getEmbeddedInteger())
              / right.getEmbeddedInteger();
          return universe.newDouble(result);
        }
      }
    });

    installInstancePrimitive(new SPrimitive("/", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SObject rightObj = args[0];
        SInteger left = (SInteger) selfO;

        // Check second parameter type:
        if (rightObj instanceof SBigInteger) {
          // Second operand was BigInteger
          return resendAsBigInteger("/", left, (SBigInteger) rightObj, frame);
        } else if (rightObj instanceof SDouble) {
          return resendAsDouble("/", left, (SDouble) rightObj, frame);
        } else {
          // Do operation:
          SInteger right = (SInteger) rightObj;

          long result = ((long) left.getEmbeddedInteger())
              / right.getEmbeddedInteger();
          return makeInt(result);
        }
      }
    });

    installInstancePrimitive(new SPrimitive("%", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SObject rightObj = args[0];
        SInteger left = (SInteger) selfO;

        // Check second parameter type:
        if (rightObj instanceof SBigInteger) {
          // Second operand was BigInteger
          return resendAsBigInteger("%", left, (SBigInteger) rightObj, frame);
        } else if (rightObj instanceof SDouble) {
          return resendAsDouble("%", left, (SDouble) rightObj, frame);
        } else {
          // Do operation:
          SInteger right = (SInteger) rightObj;

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

    installInstancePrimitive(new SPrimitive("&", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SObject rightObj = args[0];
        SInteger left = (SInteger) selfO;

        // Check second parameter type:
        if (rightObj instanceof SBigInteger) {
          // Second operand was BigInteger
          return resendAsBigInteger("&", left, (SBigInteger) rightObj, frame);
        } else if (rightObj instanceof SDouble) {
          return resendAsDouble("&", left, (SDouble) rightObj, frame);
        } else {
          // Do operation:
          SInteger right = (SInteger) rightObj;

          long result = ((long) left.getEmbeddedInteger())
              & right.getEmbeddedInteger();
          return makeInt(result);
        }
      }
    });

    installInstancePrimitive(new SPrimitive("=", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SObject rightObj = args[0];
        SInteger left = (SInteger) selfO;

        // Check second parameter type:
        if (rightObj instanceof SBigInteger) {
          // Second operand was BigInteger:
          return resendAsBigInteger("=", left, (SBigInteger) rightObj, frame);
        } else if (rightObj instanceof SInteger) {
          // Second operand was Integer:
          SInteger right = (SInteger) rightObj;

          if (left.getEmbeddedInteger() == right.getEmbeddedInteger()) {
            return universe.trueObject;
          } else {
            return universe.falseObject;
          }
        } else if (rightObj instanceof SDouble) {
          // Second operand was Integer:
          SDouble right = (SDouble) rightObj;

          if (left.getEmbeddedInteger() == right.getEmbeddedDouble()) {
            return universe.trueObject;
          } else {
            return universe.falseObject;
          }
        } else {
          return universe.falseObject;
        }
      }
    });

    installInstancePrimitive(new SPrimitive("<", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SObject rightObj = args[0];
        SInteger left = (SInteger) selfO;

        // Check second parameter type:
        if (rightObj instanceof SBigInteger) {
          // Second operand was BigInteger
          return resendAsBigInteger("<", left, (SBigInteger) rightObj, frame);
        } else if (rightObj instanceof SDouble) {
          return resendAsDouble("<", left, (SDouble) rightObj, frame);
        } else {
          // Do operation:
          SInteger right = (SInteger) rightObj;

          if (left.getEmbeddedInteger() < right.getEmbeddedInteger()) {
            return universe.trueObject;
          } else {
            return universe.falseObject;
          }
        }
      }
    });

    installClassPrimitive(new SPrimitive("fromString:", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SString param = (SString) args[0];

        long result = java.lang.Long.parseLong(param.getEmbeddedString());

        return makeInt(result);
      }
    });
  }
}
