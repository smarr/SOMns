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

import som.vm.Universe;
import som.vmobjects.Array;
import som.vmobjects.Frame;
import som.vmobjects.Integer;
import som.vmobjects.Object;
import som.vmobjects.Primitive;
import som.interpreter.Interpreter;

public class ArrayPrimitives extends Primitives {

  public ArrayPrimitives(final Universe universe) {
    super(universe);
  }

  public void installPrimitives() {
    installInstancePrimitive(new Primitive("at:", universe) {

      public void invoke(Frame frame, final Interpreter interpreter) {
        Integer index = (Integer) frame.pop();
        Array self = (Array) frame.pop();
        frame.push(self.getIndexableField(index.getEmbeddedInteger() - 1));
      }
    });

    installInstancePrimitive(new Primitive("at:put:", universe) {

      public void invoke(Frame frame, final Interpreter interpreter) {
        Object value = frame.pop();
        Integer index = (Integer) frame.pop();
        Array self = (Array) frame.getStackElement(0);
        self.setIndexableField(index.getEmbeddedInteger() - 1, value);
      }
    });

    installInstancePrimitive(new Primitive("length", universe) {

      public void invoke(Frame frame, final Interpreter interpreter) {
        Array self = (Array) frame.pop();
        frame.push(universe.newInteger(self.getNumberOfIndexableFields()));
      }
    });

    installClassPrimitive(new Primitive("new:", universe) {

      public void invoke(Frame frame, final Interpreter interpreter) {
        Integer length = (Integer) frame.pop();
        frame.pop(); // not required
        frame.push(universe.newArray(length.getEmbeddedInteger()));
      }
    });
  }
}
