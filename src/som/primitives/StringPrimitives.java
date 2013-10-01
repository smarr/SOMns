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
import som.vmobjects.SInteger;
import som.vmobjects.SObject;
import som.vmobjects.SPrimitive;
import som.vmobjects.SString;

public class StringPrimitives extends Primitives {

  public StringPrimitives(final Universe universe) {
    super(universe);
  }

  public void installPrimitives() {
    installInstancePrimitive(new SPrimitive("concatenate:", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SString argument = (SString) args[0];
        SString self = (SString) selfO;
        return universe.newString(self.getEmbeddedString()
            + argument.getEmbeddedString());
      }
    });

    installInstancePrimitive(new SPrimitive("asSymbol", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SString self = (SString) selfO;
        return universe.symbolFor(self.getEmbeddedString());
      }
    });

    installInstancePrimitive(new SPrimitive("length", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SString self = (SString) selfO;
        return universe.newInteger(self.getEmbeddedString().length());
      }
    });

    installInstancePrimitive(new SPrimitive("=", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SObject op1 = args[0];
        SString op2 = (SString) selfO;
        if (op1.getSOMClass() == universe.stringClass) {
          SString s = (SString) op1;
          if (s.getEmbeddedString().equals(op2.getEmbeddedString())) {
            return universe.trueObject;
          }
        }

        return universe.falseObject;
      }
    });

    installInstancePrimitive(new SPrimitive("primSubstringFrom:to:", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SInteger end   = (SInteger) args[1];
        SInteger start = (SInteger) args[0];

        SString self = (SString) selfO;

        try {
          return universe.newString(self.getEmbeddedString().substring(
              start.getEmbeddedInteger() - 1, end.getEmbeddedInteger()));
        } catch (IndexOutOfBoundsException e) {
          return universe.newString(new java.lang.String(
              "Error - index out of bounds"));
        }
      }
    });

    installInstancePrimitive(new SPrimitive("hashcode", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SString self = (SString) selfO;
        return universe.newInteger(self.getEmbeddedString().hashCode());
      }
    });

  }
}
