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

import som.primitives.SystemPrimsFactory.ExitPrimFactory;
import som.primitives.SystemPrimsFactory.FullGCPrimFactory;
import som.primitives.SystemPrimsFactory.GlobalPrimFactory;
import som.primitives.SystemPrimsFactory.GlobalPutPrimFactory;
import som.primitives.SystemPrimsFactory.HasGlobalPrimFactory;
import som.primitives.SystemPrimsFactory.LoadPrimFactory;
import som.primitives.SystemPrimsFactory.PrintNewlinePrimFactory;
import som.primitives.SystemPrimsFactory.PrintStringPrimFactory;
import som.primitives.SystemPrimsFactory.TicksPrimFactory;
import som.primitives.SystemPrimsFactory.TimePrimFactory;
import som.vm.Universe;

public final class SystemPrimitives extends Primitives {

  public SystemPrimitives(final Universe universe) {
    super(universe);
  }

  @Override
  public void installPrimitives() {
    installInstancePrimitive("load:",        LoadPrimFactory.getInstance());
    installInstancePrimitive("exit:",        ExitPrimFactory.getInstance());
    installInstancePrimitive("hasGlobal:",   HasGlobalPrimFactory.getInstance());
    installInstancePrimitive("global:",      GlobalPrimFactory.getInstance());
    installInstancePrimitive("global:put:",  GlobalPutPrimFactory.getInstance());
    installInstancePrimitive("printString:", PrintStringPrimFactory.getInstance());
    installInstancePrimitive("printNewline", PrintNewlinePrimFactory.getInstance());
    installInstancePrimitive("time",         TimePrimFactory.getInstance());
    installInstancePrimitive("ticks",        TicksPrimFactory.getInstance());
    installInstancePrimitive("fullGC",       FullGCPrimFactory.getInstance());
  }
}
