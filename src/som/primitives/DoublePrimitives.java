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
import som.vmobjects.Double;
import som.vmobjects.Integer;
import som.vmobjects.Object;
import som.vmobjects.Primitive;

public class DoublePrimitives extends Primitives {

  public DoublePrimitives(final Universe universe) {
    super(universe);
  }

  private Double coerceToDouble(Object o) {
    if (o instanceof Double) { return (Double) o; }
    if (o instanceof Integer) {
      return universe.newDouble((double) ((Integer) o).getEmbeddedInteger());
    }
    throw new ClassCastException("Cannot coerce to Double!");
  }

  public void installPrimitives() {
    installInstancePrimitive(new Primitive("asString", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Double self = (Double) selfO;
        return universe.newString(java.lang.Double.toString(
            self.getEmbeddedDouble()));
      }
    });

    installInstancePrimitive(new Primitive("sqrt", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Double self = (Double) selfO;
        return universe.newDouble(Math.sqrt(self.getEmbeddedDouble()));
      }
    });

    installInstancePrimitive(new Primitive("+", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Double op1 = coerceToDouble(args[0]);
        Double op2 = (Double) selfO;
        return universe.newDouble(op1.getEmbeddedDouble()
            + op2.getEmbeddedDouble());
      }
    });

    installInstancePrimitive(new Primitive("-", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Double op1 = coerceToDouble(args[0]);
        Double op2 = (Double) selfO;
        return universe.newDouble(op2.getEmbeddedDouble()
            - op1.getEmbeddedDouble());
      }
    });

    installInstancePrimitive(new Primitive("*", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Double op1 = coerceToDouble(args[0]);
        Double op2 = (Double) selfO;
        return universe.newDouble(op2.getEmbeddedDouble()
            * op1.getEmbeddedDouble());
      }
    });

    installInstancePrimitive(new Primitive("//", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Double op1 = coerceToDouble(args[0]);
        Double op2 = (Double) selfO;
        return universe.newDouble(op2.getEmbeddedDouble()
            / op1.getEmbeddedDouble());
      }
    });

    installInstancePrimitive(new Primitive("%", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Double op1 = coerceToDouble(args[0]);
        Double op2 = (Double) selfO;
        return universe.newDouble(op2.getEmbeddedDouble()
            % op1.getEmbeddedDouble());
      }
    });

    installInstancePrimitive(new Primitive("=", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Double op1 = coerceToDouble(args[0]);
        Double op2 = (Double) selfO;
        if (op1.getEmbeddedDouble() == op2.getEmbeddedDouble()) {
          return universe.trueObject;
        } else {
          return universe.falseObject;
        }
      }
    });

    installInstancePrimitive(new Primitive("<", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Double op1 = coerceToDouble(args[0]);
        Double op2 = (Double) selfO;
        if (op2.getEmbeddedDouble() < op1.getEmbeddedDouble()) {
          return universe.trueObject;
        } else {
          return universe.falseObject;
        }
      }
    });
  }
}
