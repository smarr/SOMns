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

package som.primitives;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.vm.Universe;
import som.vmobjects.Integer;
import som.vmobjects.Object;
import som.vmobjects.Primitive;
import som.vmobjects.String;

public class StringPrimitives extends Primitives {

  public StringPrimitives(final Universe universe) {
    super(universe);
  }

  public void installPrimitives() {
    installInstancePrimitive(new Primitive("concatenate:", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        String argument = (String) args[0];
        String self = (String) selfO;
        return universe.newString(self.getEmbeddedString()
            + argument.getEmbeddedString());
      }
    });

    installInstancePrimitive(new Primitive("asSymbol", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        String self = (String) selfO;
        return universe.symbolFor(self.getEmbeddedString());
      }
    });

    installInstancePrimitive(new Primitive("length", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        String self = (String) selfO;
        return universe.newInteger(self.getEmbeddedString().length());
      }
    });

    installInstancePrimitive(new Primitive("=", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Object op1 = args[0];
        String op2 = (String) selfO;
        if (op1.getSOMClass() == universe.stringClass) {
          String s = (String) op1;
          if (s.getEmbeddedString().equals(op2.getEmbeddedString())) {
            return universe.trueObject;
          }
        }

        return universe.falseObject;
      }
    });

    installInstancePrimitive(new Primitive("primSubstringFrom:to:", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Integer end   = (Integer) args[1];
        Integer start = (Integer) args[0];

        String self = (String) selfO;

        try {
          return universe.newString(self.getEmbeddedString().substring(
              start.getEmbeddedInteger(), end.getEmbeddedInteger() + 1));
        }
        catch (IndexOutOfBoundsException e) {
          return universe.newString(new java.lang.String(
              "Error - index out of bounds"));
        }
      }
    });

    installInstancePrimitive(new Primitive("hashcode", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        String self = (String) selfO;
        return universe.newInteger(self.getEmbeddedString().hashCode());
      }
    });

  }
}
