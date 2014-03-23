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

import som.vm.Universe;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeUtil.FieldOffsetProvider;

public abstract class SObject extends SAbstractObject {

  public abstract int getNumberOfFields();

  public static SObject create(final SClass instanceClass, final SObject nilObject) {
    switch (instanceClass.getNumberOfInstanceFields()) {
      case  0: return new SObject0(instanceClass);
      case  1: return new SObject1(instanceClass, nilObject);
      case  2: return new SObject2(instanceClass, nilObject);
      case  3: return new SObject3(instanceClass, nilObject);
      case  4: return new SObject4(instanceClass, nilObject);
      case  5: return new SObject5(instanceClass, nilObject);
      case  6: return new SObject6(instanceClass, nilObject);
      case  7: return new SObject7(instanceClass, nilObject);
      default: return new SObjectN(instanceClass, nilObject);
    }
  }

  public static SObject create(final int numFields, final SObject nilObject) {
    return new SObjectN(numFields, nilObject);
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

      final Field firstField = SObject1.class.getDeclaredField("field");
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

      final Field firstField  = SObject2.class.getDeclaredField("field1");
      final Field secondField = SObject2.class.getDeclaredField("field2");
      assert FIRST_OFFSET == fieldOffsetProvider.objectFieldOffset(firstField);
      return fieldOffsetProvider.objectFieldOffset(secondField) - FIRST_OFFSET;
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public abstract Object getField(final int index);
  public abstract void   setField(final int index, final Object value);

  public abstract static class SObjectKnownClass extends SObject {
    private final SClass clazz;

    protected SObjectKnownClass(final SClass instanceClass) {
      this.clazz = instanceClass;
    }

    @Override
    public final SClass getSOMClass(final Universe universe) {
      return clazz;
    }
  }

  public static final class SObject0 extends SObjectKnownClass {
    protected SObject0(final SClass instanceClass)                          { super(instanceClass); }
    @Override public Object getField(final int index)                       { assert false; return null; }
    @Override public void   setField(final int index, final Object value)   { assert false; }
    @Override public int    getNumberOfFields() { return 0; }
  }

  public static final class SObject1 extends SObjectKnownClass {
    private Object field;
    protected SObject1(final SClass instanceClass, final SObject nilObject) { super(instanceClass); field = nilObject; }
    @Override public Object getField(final int index)                       { assert index == 0; return field; }
    @Override public void   setField(final int index, final Object value)   { assert index == 0; field = value; }
    @Override public int    getNumberOfFields() { return 1; }
  }

  public static final class SObject2 extends SObjectKnownClass {
    private Object field1;
    private Object field2;
    protected SObject2(final SClass instanceClass, final SObject nilObject) { super(instanceClass); field1 = field2 = nilObject; }
    @Override public Object getField(final int index)                       { if (index == 0) { return field1;  } else { assert index == 1; return field2;  }}
    @Override public void   setField(final int index, final Object value)   { if (index == 0) { field1 = value; } else { assert index == 1; field2 = value; }}
    @Override public int    getNumberOfFields() { return 2; }
  }

  public static final class SObject3 extends SObjectKnownClass {
    private Object field1;
    private Object field2;
    private Object field3;
    protected SObject3(final SClass instanceClass, final SObject nilObject) { super(instanceClass); field1 = field2 = field3 = nilObject; }
    @Override public Object getField(final int index)                       {
      if (index == 0) { return field1;  } else
      if (index == 1) { return field2;  } else { assert index == 2; return field3;  }}
    @Override public void   setField(final int index, final Object value)   {
      if (index == 0) { field1 = value; } else
      if (index == 1) { field2 = value; } else { assert index == 2; field3 = value; }}
    @Override public int    getNumberOfFields() { return 3; }
  }

  public static final class SObject4 extends SObjectKnownClass {
    private Object field1;
    private Object field2;
    private Object field3;
    private Object field4;
    protected SObject4(final SClass instanceClass, final SObject nilObject) { super(instanceClass); field1 = field2 = field3 = field4 = nilObject; }
    @Override public Object getField(final int index)                       {
      if (index == 0) { return field1;  } else
      if (index == 1) { return field2;  } else
      if (index == 2) { return field3;  } else { assert index == 3; return field4;  }}
    @Override public void   setField(final int index, final Object value)   {
      if (index == 0) { field1 = value; } else
      if (index == 1) { field2 = value; } else
      if (index == 2) { field3 = value; } else { assert index == 3; field4 = value; }}
    @Override public int    getNumberOfFields() { return 4; }
  }

  public static final class SObject5 extends SObjectKnownClass {
    private Object field1;
    private Object field2;
    private Object field3;
    private Object field4;
    private Object field5;
    protected SObject5(final SClass instanceClass, final SObject nilObject) { super(instanceClass); field1 = field2 = field3 = field4 = field5 = nilObject; }
    @Override public Object getField(final int index)                       {
      if (index == 0) { return field1;  } else
      if (index == 1) { return field2;  } else
      if (index == 2) { return field3;  } else
      if (index == 3) { return field4;  } else { assert index == 4; return field5;  }}
    @Override public void   setField(final int index, final Object value)   {
      if (index == 0) { field1 = value; } else
      if (index == 1) { field2 = value; } else
      if (index == 2) { field3 = value; } else
      if (index == 3) { field4 = value; } else { assert index == 4; field5 = value; }}
    @Override public int    getNumberOfFields() { return 5; }
  }

  public static final class SObject6 extends SObjectKnownClass {
    private Object field1;
    private Object field2;
    private Object field3;
    private Object field4;
    private Object field5;
    private Object field6;
    protected SObject6(final SClass instanceClass, final SObject nilObject) { super(instanceClass); field1 = field2 = field3 = field4 = field5 = field6 = nilObject; }
    @Override public Object getField(final int index)                       {
      if (index == 0) { return field1;  } else
      if (index == 1) { return field2;  } else
      if (index == 2) { return field3;  } else
      if (index == 3) { return field4;  } else
      if (index == 4) { return field5;  } else { assert index == 5; return field6; }}
    @Override public void   setField(final int index, final Object value)   {
      if (index == 0) { field1 = value; } else
      if (index == 1) { field2 = value; } else
      if (index == 2) { field3 = value; } else
      if (index == 3) { field4 = value; } else
      if (index == 4) { field5 = value; } else { assert index == 5; field6 = value; }}
    @Override public int    getNumberOfFields() { return 6; }
  }

  public static final class SObject7 extends SObjectKnownClass {
    private Object field1;
    private Object field2;
    private Object field3;
    private Object field4;
    private Object field5;
    private Object field6;
    private Object field7;
    protected SObject7(final SClass instanceClass, final SObject nilObject) { super(instanceClass); field1 = field2 = field3 = field4 = field5 = field6 = field7 = nilObject; }
    @Override public Object getField(final int index)                       {
      if (index == 0) { return field1;  } else
      if (index == 1) { return field2;  } else
      if (index == 2) { return field3;  } else
      if (index == 3) { return field4;  } else
      if (index == 4) { return field5;  } else
      if (index == 5) { return field6;  } else { assert index == 6; return field7;  }}
    @Override public void   setField(final int index, final Object value)   {
      if (index == 0) { field1 = value; } else
      if (index == 1) { field2 = value; } else
      if (index == 2) { field3 = value; } else
      if (index == 3) { field4 = value; } else
      if (index == 4) { field5 = value; } else
      if (index == 5) { field6 = value; } else { assert index == 6; field7 = value; }}
    @Override public int    getNumberOfFields() { return 7; }
  }

  public static class SObjectN extends SObject {
    private final Object[] fields;
    @CompilationFinal private SClass clazz;

    protected SObjectN(final int numberOfFields, final SObject nilObject) {
      fields = setClearFields(numberOfFields, nilObject);
    }

    protected SObjectN(final SClass instanceClass, final SObject nilObject) {
      fields = setClearFields(instanceClass.getNumberOfInstanceFields(), nilObject);
      clazz  = instanceClass;
    }

    @Override
    public final Object getField(final int index) {
      return fields[index];
    }

    @Override
    public final void setField(final int index, final Object value) {
      fields[index] = value;
    }

    @ExplodeLoop
    private Object[] setClearFields(final int numberOfFields, final SObject nilObject) {
      // Clear each and every field by putting nil into them
      Object[] fieldArr = new Object[numberOfFields];
      for (int i = 0; i < fieldArr.length; i++) {
        fieldArr[i] = nilObject;
      }
      return fieldArr;
    }

    @Override
    public final int getNumberOfFields() {
      // Get the number of fields in this object
      return fields.length;
    }

    @Override
    public final SClass getSOMClass(final Universe universe) {
      // Get the class of this object by reading the field with class index
      return clazz;
    }

    public final void setClass(final SClass value) {
      transferToInterpreterAndInvalidate("SObject.setClass");
      // Set the class of this object by writing to the field with class index
      clazz = value;
    }
  }

  // Static field indices and number of object fields
  static final int numberOfObjectFields = 0;
}
