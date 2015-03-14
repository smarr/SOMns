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

import som.primitives.IntegerPrimsFactory.As32BitSignedValueFactory;
import som.primitives.IntegerPrimsFactory.As32BitUnsignedValueFactory;
import som.primitives.IntegerPrimsFactory.FromStringPrimFactory;
import som.primitives.IntegerPrimsFactory.LeftShiftPrimFactory;
import som.primitives.IntegerPrimsFactory.MaxIntPrimFactory;
import som.primitives.IntegerPrimsFactory.RandomPrimFactory;
import som.primitives.IntegerPrimsFactory.UnsignedRightShiftPrimFactory;
import som.primitives.arithmetic.AdditionPrimFactory;
import som.primitives.arithmetic.BitXorPrimFactory;
import som.primitives.arithmetic.DividePrimFactory;
import som.primitives.arithmetic.DoubleDivPrimFactory;
import som.primitives.arithmetic.LessThanPrimFactory;
import som.primitives.arithmetic.LogicAndPrimFactory;
import som.primitives.arithmetic.ModuloPrimFactory;
import som.primitives.arithmetic.MultiplicationPrimFactory;
import som.primitives.arithmetic.SqrtPrimFactory;
import som.primitives.arithmetic.SubtractionPrimFactory;

public final class IntegerPrimitives extends Primitives {

  @Override
  public void installPrimitives() {
    installInstancePrimitive("asString", AsStringPrimFactory.getInstance());
    installInstancePrimitive("sqrt",     SqrtPrimFactory.getInstance());
    installInstancePrimitive("atRandom", RandomPrimFactory.getInstance());
    installInstancePrimitive("+",        AdditionPrimFactory.getInstance());
    installInstancePrimitive("-",        SubtractionPrimFactory.getInstance());
    installInstancePrimitive("*",        MultiplicationPrimFactory.getInstance());
    installInstancePrimitive("//",       DoubleDivPrimFactory.getInstance());
    installInstancePrimitive("/",        DividePrimFactory.getInstance());
    installInstancePrimitive("%",        ModuloPrimFactory.getInstance());
    installInstancePrimitive("&",        LogicAndPrimFactory.getInstance());
    installInstancePrimitive("=",        EqualsPrimFactory.getInstance());
    installInstancePrimitive("<",        LessThanPrimFactory.getInstance());

    installInstancePrimitive("<<",       LeftShiftPrimFactory.getInstance());
    installInstancePrimitive(">>>",      UnsignedRightShiftPrimFactory.getInstance());
    installInstancePrimitive("bitXor:",  BitXorPrimFactory.getInstance());
    installInstancePrimitive("max:",     MaxIntPrimFactory.getInstance());

    installInstancePrimitive("as32BitSignedValue",   As32BitSignedValueFactory.getInstance());
    installInstancePrimitive("as32BitUnsignedValue", As32BitUnsignedValueFactory.getInstance());

    installClassPrimitive("fromString:", FromStringPrimFactory.getInstance());
  }
}
