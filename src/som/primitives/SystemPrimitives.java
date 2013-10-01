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
import som.vmobjects.SClass;
import som.vmobjects.SInteger;
import som.vmobjects.SObject;
import som.vmobjects.SPrimitive;
import som.vmobjects.SString;
import som.vmobjects.SSymbol;

public class SystemPrimitives extends Primitives {

  public SystemPrimitives(final Universe universe) {
    super(universe);
  }

  public void installPrimitives() {
    installInstancePrimitive(new SPrimitive("load:", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SSymbol argument = (SSymbol) args[0];

        SClass result = universe.loadClass(argument);
        return result != null ? result : universe.nilObject;
      }
    });

    installInstancePrimitive(new SPrimitive("exit:", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SInteger error = (SInteger) args[0];
        universe.exit(error.getEmbeddedInteger());
        return selfO;
      }
    });

    installInstancePrimitive(new SPrimitive("global:", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SSymbol argument = (SSymbol) args[0];

        SObject result = universe.getGlobal(argument);
        return result != null ? result : universe.nilObject;
      }
    });

    installInstancePrimitive(new SPrimitive("global:put:", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SObject value    = args[1];
        SSymbol argument = (SSymbol) args[0];
        universe.setGlobal(argument, value);
        return value;
      }
    });

    installInstancePrimitive(new SPrimitive("printString:", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        SString argument = (SString) args[0];
        Universe.print(argument.getEmbeddedString());
        return selfO;
      }
    });

    installInstancePrimitive(new SPrimitive("printNewline", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        Universe.println();
        return selfO;
      }
    });

    startMicroTime = System.nanoTime() / 1000L;
    startTime = startMicroTime / 1000L;
    installInstancePrimitive(new SPrimitive("time", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        int time = (int) (System.currentTimeMillis() - startTime);
        return universe.newInteger(time);
      }
    });

    installInstancePrimitive(new SPrimitive("ticks", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        int time = (int) (System.nanoTime() / 1000L - startMicroTime);
        return universe.newInteger(time);
      }
    });

    installInstancePrimitive(new SPrimitive("fullGC", universe) {

      public SObject invoke(final PackedFrame frame, final SObject selfO, final SObject[] args) {
        System.gc();
        return universe.trueObject;
      }
    });

  }

  private long startTime;
  private long startMicroTime;
}
