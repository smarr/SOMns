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

import som.interpreter.Interpreter;
import som.vm.Universe;
import som.vmobjects.Frame;
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

      public void invoke(Frame frame, final Interpreter interpreter) {
        String argument = (String) frame.pop();
        String self = (String) frame.pop();
        frame.push(universe.newString(self.getEmbeddedString()
            + argument.getEmbeddedString()));
      }
    });

    installInstancePrimitive(new Primitive("asSymbol", universe) {

      public void invoke(Frame frame, final Interpreter interpreter) {
        String self = (String) frame.pop();
        frame.push(universe.symbolFor(self.getEmbeddedString()));
      }
    });

    installInstancePrimitive(new Primitive("length", universe) {

      public void invoke(Frame frame, final Interpreter interpreter) {
        String self = (String) frame.pop();
        frame.push(universe.newInteger(self.getEmbeddedString().length()));
      }
    });

    installInstancePrimitive(new Primitive("=", universe) {

      public void invoke(Frame frame, final Interpreter interpreter) {
        Object op1 = frame.pop();
        String op2 = (String) frame.pop(); // self
        if (op1.getSOMClass() == universe.stringClass) {
          String s = (String) op1;
          if (s.getEmbeddedString().equals(op2.getEmbeddedString())) {
            frame.push(universe.trueObject);
            return;
          }
        }

        frame.push(universe.falseObject);
      }
    });

    installInstancePrimitive(new Primitive("primSubstringFrom:to:", universe) {

      public void invoke(Frame frame, final Interpreter interpreter) {
        Integer end = (Integer) frame.pop();
        Integer start = (Integer) frame.pop();

        String self = (String) frame.pop();

        try {
          frame.push(universe.newString(self.getEmbeddedString().substring(
              start.getEmbeddedInteger(), end.getEmbeddedInteger() + 1)));
        }
        catch (IndexOutOfBoundsException e) {
          frame.push(universe.newString(new java.lang.String(
              "Error - index out of bounds")));
        }
      }
    });

    installInstancePrimitive(new Primitive("hashcode", universe) {

      public void invoke(Frame frame, final Interpreter interpreter) {
        String self = (String) frame.pop();
        frame.push(universe.newInteger(self.getEmbeddedString().hashCode()));
      }
    });

  }
}
