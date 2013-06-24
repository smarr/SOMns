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
import som.vmobjects.Class;
import som.vmobjects.Integer;
import som.vmobjects.Object;
import som.vmobjects.Primitive;
import som.vmobjects.String;
import som.vmobjects.Symbol;

public class SystemPrimitives extends Primitives {

  public SystemPrimitives(final Universe universe) {
    super(universe);
  }

  public void installPrimitives() {
    installInstancePrimitive(new Primitive("load:", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Symbol argument = (Symbol) args[0];
        
        Class result = universe.loadClass(argument);
        return result != null ? result : universe.nilObject;
      }
    });

    installInstancePrimitive(new Primitive("exit:", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Integer error = (Integer) args[0];
        universe.exit(error.getEmbeddedInteger());
        return selfO;
      }
    });

    installInstancePrimitive(new Primitive("global:", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Symbol argument = (Symbol) args[0];
        
        Object result = universe.getGlobal(argument);
        return result != null ? result : universe.nilObject;
      }
    });

    installInstancePrimitive(new Primitive("global:put:", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        Object value    = args[1];
        Symbol argument = (Symbol) args[0];
        universe.setGlobal(argument, value);
        return value;
      }
    });

    installInstancePrimitive(new Primitive("printString:", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        String argument = (String) args[0];
        System.out.print(argument.getEmbeddedString());
        return selfO;
      }
    });

    installInstancePrimitive(new Primitive("printNewline", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        System.out.println("");
        return selfO;
      }
    });

    startMicroTime = System.nanoTime() / 1000L;
    startTime = startMicroTime / 1000L;
    installInstancePrimitive(new Primitive("time", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        int time = (int) (System.currentTimeMillis() - startTime);
        return universe.newInteger(time);
      }
    });

    installInstancePrimitive(new Primitive("ticks", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        int time = (int) (System.nanoTime() / 1000L - startMicroTime);
        return universe.newInteger(time);
      }
    });

    installInstancePrimitive(new Primitive("fullGC", universe) {

      public Object invoke(final VirtualFrame frame, final Object selfO, final Object[] args) {
        // naught - GC is entirely left to the JVM
        return selfO;
      }
    });

  }

  private long startTime;
  private long startMicroTime;
}
