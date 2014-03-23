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

package som.compiler;

import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;

public final class Disassembler {

  @SlowPath
  public static void dump(final SClass cl) {
    for (int i = 0; i < cl.getNumberOfInstanceInvokables(); i++) {
      SInvokable inv = cl.getInstanceInvokable(i);

      // output header and skip if the Invokable is a Primitive
      Universe.errorPrint(cl.getName().toString() + ">>"
          + inv.getSignature().toString() + " = ");

      // output actual method
      dumpMethod(inv, "\t");
    }
  }

  @SlowPath
  public static void dumpMethod(final SInvokable m, final String indent) {
    Universe.errorPrintln("(");
    Universe.errorPrintln(m.getInvokable().toString());
    Universe.errorPrintln(indent + ")");
  }

}

