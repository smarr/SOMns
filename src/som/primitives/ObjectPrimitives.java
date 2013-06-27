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
import som.vmobjects.Array;
import som.vmobjects.Class;
import som.vmobjects.Integer;
import som.vmobjects.Invokable;
import som.vmobjects.Object;
import som.vmobjects.Primitive;
import som.vmobjects.Symbol;

public class ObjectPrimitives extends Primitives {

  public ObjectPrimitives(final Universe universe) {
    super(universe);
  }

  public void installPrimitives() {

    installInstancePrimitive(new Primitive("==", universe) {
      public Object invoke(final VirtualFrame frame, final Object self, final Object[] args) {
        if (self == args[0]) {
          return universe.trueObject;
        } else {
          return universe.falseObject;
        }
      }
    });

    installInstancePrimitive(new Primitive("hashcode", universe) {
      public Object invoke(final VirtualFrame frame, final Object self, final Object[] args) {
        return universe.newInteger(self.hashCode());
      }
    });

    installInstancePrimitive(new Primitive("objectSize", universe) {
      public Object invoke(final VirtualFrame frame, final Object self, final Object[] args) {
        int size = self.getNumberOfFields();
        if (self instanceof Array) {
          size += ((Array) self).getNumberOfIndexableFields();
        }
        return universe.newInteger(size);
      }
    });

    installInstancePrimitive(new Primitive("perform:", universe) {
      public Object invoke(final VirtualFrame frame, final Object self, final Object[] args) {
        Symbol selector = (Symbol) args[0];

        Invokable invokable = self.getSOMClass().lookupInvokable(selector);
        return invokable.invoke(frame, self, null);
      }
    });

    installInstancePrimitive(new Primitive("perform:inSuperclass:", universe) {
      public Object invoke(final VirtualFrame frame, final Object self, final Object[] args) {
        Symbol selector = (Symbol) args[0];
        Class  clazz    = (Class)  args[1];

        Invokable invokable = clazz.lookupInvokable(selector);
        return invokable.invoke(frame, self, null);
      }
    });

    installInstancePrimitive(new Primitive("perform:withArguments:", universe) {
      public Object invoke(final VirtualFrame frame, final Object self, final Object[] args) {
        Symbol selector = (Symbol) args[0];
        Array  argsArr  = (Array)  args[1];

        Invokable invokable = self.getSOMClass().lookupInvokable(selector);
        return invokable.invoke(frame, self, argsArr.indexableFields);
      }
    });

    installInstancePrimitive(new Primitive("instVarAt:", universe) {
      public Object invoke(final VirtualFrame frame, final Object self, final Object[] args) {
        Integer idx = (Integer) args[0];

        return self.getField(idx.getEmbeddedInteger() - 1);
      }
    });

    installInstancePrimitive(new Primitive("instVarAt:put:", universe) {
      public Object invoke(final VirtualFrame frame, final Object self, final Object[] args) {
        Integer idx = (Integer) args[0];
        Object val  = args[1];

        self.setField(idx.getEmbeddedInteger() - 1, val);

        return val;
      }
    });
  }
}
