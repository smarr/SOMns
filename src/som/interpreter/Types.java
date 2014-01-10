/**
 * Copyright (c) 2013 Stefan Marr, stefan.marr@vub.ac.be
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
package som.interpreter;

import java.math.BigInteger;

import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SBigInteger;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SString;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.TypeSystem;

@TypeSystem({       int.class,
             BigInteger.class,
                 String.class,
                 double.class,
                 SClass.class,
                SObject.class,
               SInteger.class,
                SDouble.class,
            SBigInteger.class,
                 SBlock.class,
                 SArray.class,
                SString.class,
                SSymbol.class,
                SMethod.class,
        SAbstractObject.class,
               Object[].class})
public class Types {

  public static final SAbstractObject asAbstractObject(final Object obj,
      final Universe universe) {
    if (obj instanceof SAbstractObject) {
      return (SAbstractObject) obj;
    } else if (obj instanceof Integer) {
      return universe.newInteger((int) obj);
    } else if (obj instanceof BigInteger) {
      return universe.newBigInteger((BigInteger) obj);
    } else if (obj instanceof String) {
      return universe.newString((String) obj);
    } else if (obj instanceof Double) {
      return universe.newDouble((double) obj);
    }

    throw new RuntimeException("We got an object that should be covered by the above check: " + obj.toString());
  }

  public static SClass getClassOf(final Object obj, final Universe universe) {
    if (obj instanceof SAbstractObject) {
      return ((SAbstractObject) obj).getSOMClass(universe);
    } else if (obj instanceof Integer) {
      return universe.integerClass;
    } else if (obj instanceof BigInteger) {
      return universe.bigintegerClass;
    } else if (obj instanceof String) {
      return universe.stringClass;
    } else if (obj instanceof Double) {
      return universe.doubleClass;
    }

    throw new RuntimeException("We got an object that should be covered by the above check: " + obj.toString());
  }

  @ImplicitCast
  public int castInteger(final SInteger i) {
    return i.getEmbeddedInteger();
  }

  @ImplicitCast
  public BigInteger castBigInteger(final SBigInteger i) {
    return i.getEmbeddedBiginteger();
  }

  @ImplicitCast
  public String castString(final SString str) {
    return str.getEmbeddedString();
  }

  @ImplicitCast
  public double castDouble(final SDouble d) {
    return d.getEmbeddedDouble();
  }
}
