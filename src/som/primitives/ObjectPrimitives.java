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
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SInteger;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SPrimitive;
import som.vmobjects.SSymbol;

public class ObjectPrimitives extends Primitives {

  public ObjectPrimitives(final Universe universe) {
    super(universe);
  }

  public void installPrimitives() {

    installInstancePrimitive(new SPrimitive("==", universe) {
      public SObject invoke(final PackedFrame frame, final SObject self, final SObject[] args) {
        if (self == args[0]) {
          return universe.trueObject;
        } else {
          return universe.falseObject;
        }
      }
    });

    installInstancePrimitive(new SPrimitive("hashcode", universe) {
      public SObject invoke(final PackedFrame frame, final SObject self, final SObject[] args) {
        return universe.newInteger(self.hashCode());
      }
    });

    installInstancePrimitive(new SPrimitive("objectSize", universe) {
      public SObject invoke(final PackedFrame frame, final SObject self, final SObject[] args) {
        int size = self.getNumberOfFields();
        if (self instanceof SArray) {
          size += ((SArray) self).getNumberOfIndexableFields();
        }
        return universe.newInteger(size);
      }
    });

    installInstancePrimitive(new SPrimitive("perform:", universe) {
      public SObject invoke(final PackedFrame frame, final SObject self, final SObject[] args) {
        SSymbol selector = (SSymbol) args[0];

        SInvokable invokable = self.getSOMClass().lookupInvokable(selector);
        return invokable.invoke(frame, self, null);
      }
    });

    installInstancePrimitive(new SPrimitive("perform:inSuperclass:", universe) {
      public SObject invoke(final PackedFrame frame, final SObject self, final SObject[] args) {
        SSymbol selector = (SSymbol) args[0];
        SClass  clazz    = (SClass)  args[1];

        SInvokable invokable = clazz.lookupInvokable(selector);
        return invokable.invoke(frame, self, null);
      }
    });

    installInstancePrimitive(new SPrimitive("perform:withArguments:", universe) {
      public SObject invoke(final PackedFrame frame, final SObject self, final SObject[] args) {
        SSymbol selector = (SSymbol) args[0];
        SArray  argsArr  = (SArray)  args[1];

        SInvokable invokable = self.getSOMClass().lookupInvokable(selector);
        return invokable.invoke(frame, self, argsArr.indexableFields);
      }
    });

    installInstancePrimitive(new SPrimitive("instVarAt:", universe) {
      public SObject invoke(final PackedFrame frame, final SObject self, final SObject[] args) {
        SInteger idx = (SInteger) args[0];

        return self.getField(idx.getEmbeddedInteger() - 1);
      }
    });

    installInstancePrimitive(new SPrimitive("instVarAt:put:", universe) {
      public SObject invoke(final PackedFrame frame, final SObject self, final SObject[] args) {
        SInteger idx = (SInteger) args[0];
        SObject val  = args[1];

        self.setField(idx.getEmbeddedInteger() - 1, val);

        return val;
      }
    });

    installInstancePrimitive(new SPrimitive("halt", universe) {
      public SObject invoke(final PackedFrame frame, final SObject self, final SObject[] args) {
        Universe.errorPrintln("BREAKPOINT");
        return self;
      }
    });
  }
}
