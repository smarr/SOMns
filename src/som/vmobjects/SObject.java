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

package som.vmobjects;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;

import java.lang.reflect.Field;
import java.util.Arrays;

import som.vm.Universe;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeUtil.FieldOffsetProvider;

public class SObject extends SAbstractObject {

  @CompilationFinal protected SClass clazz;

  public static final int NUM_DIRECT_FIELDS = 5;
  private Object field1;
  private Object field2;
  private Object field3;
  private Object field4;
  private Object field5;
  private Object[] extensionFields;
  private int    numberOfFields;

  protected SObject(final SClass instanceClass, final SObject nilObject) {
    this(instanceClass.getNumberOfInstanceFields(), nilObject);
    clazz = instanceClass;
  }

  protected SObject(final int numFields, final SObject nilObject) {
    field1 = field2 = field3 = field4 = field5 = nilObject;
    numberOfFields = numFields;
    if (numberOfFields > NUM_DIRECT_FIELDS) {
      extensionFields = new Object[numberOfFields - NUM_DIRECT_FIELDS];
      Arrays.fill(extensionFields, nilObject);
    } else {
      extensionFields = NO_FIELDS;
    }
  }

  @Override
  public final SClass getSOMClass(final Universe universe) {
    return clazz;
  }

  public static final SObject create(final SClass instanceClass, final SObject nilObject) {
    return new SObject(instanceClass, nilObject);
  }

  public static SObject create(final int numFields, final SObject nilObject) {
    return new SObject(numFields, nilObject);
  }

  public static final long FIRST_OFFSET = getFirstOffset();
  public static final long FIELD_LENGTH = getFieldLength();

  private static long getFirstOffset() {
    try {
      final Field fieldOffsetProviderField =
          NodeUtil.class.getDeclaredField("unsafeFieldOffsetProvider");
      fieldOffsetProviderField.setAccessible(true);
      final FieldOffsetProvider fieldOffsetProvider =
          (FieldOffsetProvider) fieldOffsetProviderField.get(null);

      final Field firstField = SObject.class.getDeclaredField("field1");
      return fieldOffsetProvider.objectFieldOffset(firstField);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static long getFieldLength() {
    try {
      final Field fieldOffsetProviderField =
          NodeUtil.class.getDeclaredField("unsafeFieldOffsetProvider");
      fieldOffsetProviderField.setAccessible(true);
      final FieldOffsetProvider fieldOffsetProvider =
          (FieldOffsetProvider) fieldOffsetProviderField.get(null);

      final Field firstField  = SObject.class.getDeclaredField("field1");
      final Field secondField = SObject.class.getDeclaredField("field2");
      assert FIRST_OFFSET == fieldOffsetProvider.objectFieldOffset(firstField);
      return fieldOffsetProvider.objectFieldOffset(secondField) - FIRST_OFFSET;
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public final Object getField(final int index) {
    switch (index) {
      case  0: return field1;
      case  1: return field2;
      case  2: return field3;
      case  3: return field4;
      case  4: return field5;
      default: return extensionFields[index - NUM_DIRECT_FIELDS];
    }
  }

  public final void setField(final int index, final Object value) {
    switch (index) {
      case  0: field1 = value; break;
      case  1: field2 = value; break;
      case  2: field3 = value; break;
      case  3: field4 = value; break;
      case  4: field5 = value; break;
      default: extensionFields[index - NUM_DIRECT_FIELDS] = value;  break;
    }
  }

  public final Object getExtensionField(final int extensionIndex) {
    return extensionFields[extensionIndex];
  }

  public final void setExtensionField(final int extensionIndex, final Object value) {
    extensionFields[extensionIndex] = value;
  }

  public final int getNumberOfFields() {
    return numberOfObjectFields;
  }

  public final void setClass(final SClass value) {
    transferToInterpreterAndInvalidate("SObject.setClass");
    // Set the class of this object by writing to the field with class index
    clazz = value;
  }

  // Static field indices and number of object fields
  static final int numberOfObjectFields = 0;
  static final Object[] NO_FIELDS = new Object[0];
}
