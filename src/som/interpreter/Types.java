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

import com.oracle.truffle.api.dsl.TypeCast;
import com.oracle.truffle.api.dsl.TypeCheck;
import com.oracle.truffle.api.dsl.TypeSystem;

@TypeSystem({       int.class,
             BigInteger.class,
                boolean.class,
                 String.class,
                 double.class,
   som.vmobjects.Object.class})
public class Types {

  @TypeCheck
  public boolean isInteger(Object value) {
    return value instanceof Integer               ||
           value instanceof som.vmobjects.Integer ||
          (value instanceof BigInteger && ((BigInteger) value).bitLength() < Integer.SIZE) ||
          (value instanceof som.vmobjects.BigInteger &&
              ((som.vmobjects.BigInteger) value).getEmbeddedBiginteger().bitLength() < Integer.SIZE);
  }

  @TypeCast
  public int asInteger(Object value) {
    assert isInteger(value);
    if (value instanceof Integer) {
      return (int) value;
    } else if (value instanceof som.vmobjects.Integer) {
      return ((som.vmobjects.Integer) value).getEmbeddedInteger();
    } else {
      BigInteger val;
      if (value instanceof BigInteger) {
        val = (BigInteger) value;
      } else {
        val = ((som.vmobjects.BigInteger) value).getEmbeddedBiginteger();
      }
      int result = val.intValue();
      assert BigInteger.valueOf(result).equals(value) : "Losing precision";
      return result;
    }
  }

  @TypeCheck
  public boolean isBigInteger(Object value) {
    return value instanceof BigInteger ||
           value instanceof som.vmobjects.BigInteger;
  }

  @TypeCast
  public BigInteger asBigInteger(Object value) {
    assert isBigInteger(value);
    if (value instanceof BigInteger) {
      return (BigInteger) value;
    } else {
      return ((som.vmobjects.BigInteger) value).getEmbeddedBiginteger();
    }
  }

  @TypeCheck
  public boolean isBoolean(Object value) {
    return value instanceof Boolean ||
           Universe.current().trueObject == value ||
           Universe.current().falseObject == value;
  }

  @TypeCast
  public boolean asBoolean(Object value) {
    assert isBoolean(value);
    if (value instanceof Boolean) {
      return (boolean) value;
    } else if (Universe.current().trueObject == value) {
      return true;
    } else {
      assert Universe.current().falseObject == value;
      return false;
    }
  }

  @TypeCheck
  public boolean isString(Object value) {
    return value instanceof String ||
           value instanceof som.vmobjects.String;
  }

  @TypeCast
  public String asString(Object value) {
    assert isString(value);
    if (value instanceof String) {
      return (String) value;
    } else {
      return ((som.vmobjects.String) value).getEmbeddedString();
    }
  }

  @TypeCheck
  public boolean isDouble(Object value) {
    return value instanceof Double ||
           value instanceof som.vmobjects.Double;
  }

  @TypeCast
  public double asDouble(Object value) {
    assert isDouble(value);
    if (value instanceof Double) {
      return (double) value;
    } else {
      return ((som.vmobjects.Double) value).getEmbeddedDouble();
    }
  }

  // TODO: clear with truffle team why this is overridden in the generated class
  @TypeCheck
  public boolean isObject(java.lang.Object value) {
    return value instanceof som.vmobjects.Object; // ||
//           value instanceof Boolean ||
//           value instanceof Double  ||
//           value instanceof Integer ||
//           value instanceof BigInteger ||
//           value instanceof String;
  }

  // TODO: why is that currently not really supported (isObject is overridden)
  // TODO: this is also problematic, because I need to do the 'conversion' explicitly in all executeGeneric()
  @TypeCast
  public som.vmobjects.Object asObject(Object value) {
    assert isObject(value);
    if (value instanceof som.vmobjects.Object) {
      return (som.vmobjects.Object) value;
    } else {
      Universe u = Universe.current();
      if (value instanceof Boolean) {
        if ((boolean) value) {
          return u.trueObject;
        } else {
          return u.falseObject;
        }
      } else if (value instanceof Double) {
        return u.newDouble((double) value);
      } else if (value instanceof Integer) {
        return u.newInteger((int) value);
      } else if (value instanceof BigInteger) {
        return u.newBigInteger((BigInteger) value);
      } else {
        assert value instanceof String;
        return u.newString((String) value);
      }
    }
  }
}
