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

import som.interpreter.nodes.specialized.WhilePrimitiveNodeFactory.WhileFalsePrimitiveNodeFactory;
import som.interpreter.nodes.specialized.WhilePrimitiveNodeFactory.WhileTruePrimitiveNodeFactory;
import som.primitives.BlockPrimsFactory.RestartPrimFactory;
import som.primitives.BlockPrimsFactory.ValueMorePrimFactory;
import som.primitives.BlockPrimsFactory.ValueNonePrimFactory;
import som.primitives.BlockPrimsFactory.ValueOnePrimFactory;
import som.primitives.BlockPrimsFactory.ValueTwoPrimFactory;

public final class BlockPrimitives extends Primitives {
  @Override
  public void installPrimitives() {
    if (holder == universe.getBlockClass(0) || universe.getBlockClass(0) == null) {
      installInstancePrimitive("restart",          RestartPrimFactory.getInstance());
      installInstancePrimitive("whileTrue:",       WhileTruePrimitiveNodeFactory.getInstance());
      installInstancePrimitive("whileFalse:",      WhileFalsePrimitiveNodeFactory.getInstance());
    } else if (universe.getBlockClass(0) != null) {
      if (holder == universe.getBlockClass(1)) {
        installInstancePrimitive("value",            ValueNonePrimFactory.getInstance());
      } else if (holder == universe.getBlockClass(2)) {
        installInstancePrimitive("value:",           ValueOnePrimFactory.getInstance());
      } else if (holder == universe.getBlockClass(3)) {
        installInstancePrimitive("value:with:",      ValueTwoPrimFactory.getInstance());
      } else if (holder == universe.getBlockClass(4)) {
        installInstancePrimitive("value:with:with:", ValueMorePrimFactory.getInstance());
      }
    }
  }
}
