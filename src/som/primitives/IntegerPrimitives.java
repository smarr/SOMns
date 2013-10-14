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

import som.primitives.IntegerPrimsFactory.AndPrimFactory;
import som.primitives.IntegerPrimsFactory.AsStringPrimFactory;
import som.primitives.IntegerPrimsFactory.DivPrimFactory;
import som.primitives.IntegerPrimsFactory.DoubleDivPrimFactory;
import som.primitives.IntegerPrimsFactory.EqualsPrimFactory;
import som.primitives.IntegerPrimsFactory.FromStringPrimFactory;
import som.primitives.IntegerPrimsFactory.LessThanPrimFactory;
import som.primitives.IntegerPrimsFactory.ModPrimFactory;
import som.primitives.IntegerPrimsFactory.RandomPrimFactory;
import som.primitives.IntegerPrimsFactory.SqrtPrimFactory;
import som.primitives.arithmetic.AdditionPrimFactory;
import som.primitives.arithmetic.MultiplicationPrimFactory;
import som.primitives.arithmetic.SubtractionPrimFactory;
import som.vm.Universe;

public class IntegerPrimitives extends Primitives {

  public IntegerPrimitives(final Universe universe) {
    super(universe);
  }

  @Override
  public void installPrimitives() {
    installInstancePrimitive("asString", AsStringPrimFactory.getInstance());
    installInstancePrimitive("sqrt",     SqrtPrimFactory.getInstance());
    installInstancePrimitive("atRandom", RandomPrimFactory.getInstance());
    installInstancePrimitive("+",        AdditionPrimFactory.getInstance());
    installInstancePrimitive("-",        SubtractionPrimFactory.getInstance());
    installInstancePrimitive("*",        MultiplicationPrimFactory.getInstance());
    installInstancePrimitive("//",       DoubleDivPrimFactory.getInstance());
    installInstancePrimitive("/",        DivPrimFactory.getInstance());
    installInstancePrimitive("%",        ModPrimFactory.getInstance());
    installInstancePrimitive("&",        AndPrimFactory.getInstance());
    installInstancePrimitive("=",        EqualsPrimFactory.getInstance());
    installInstancePrimitive("<",        LessThanPrimFactory.getInstance());

    installClassPrimitive("fromString:", FromStringPrimFactory.getInstance());
  }
}
