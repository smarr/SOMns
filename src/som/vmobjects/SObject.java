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

import som.interpreter.objectstorage.ObjectLayout;
import som.interpreter.objectstorage.StorageLocation;
import som.interpreter.objectstorage.StorageLocation.GeneralizeStorageLocationException;
import som.interpreter.objectstorage.StorageLocation.UninitalizedStorageLocationException;
import som.vm.Universe;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeUtil.FieldOffsetProvider;

public class SObject extends SAbstractObject {

  @CompilationFinal protected SClass clazz;

  public static final int NUM_PRIMITIVE_FIELDS = 5;
  public static final int NUM_OBJECT_FIELDS    = 5;

  @SuppressWarnings("unused")  private long   primField1;
  @SuppressWarnings("unused")  private long   primField2;
  @SuppressWarnings("unused")  private long   primField3;
  @SuppressWarnings("unused")  private long   primField4;
  @SuppressWarnings("unused")  private long   primField5;

  @SuppressWarnings("unused")  private Object field1;
  @SuppressWarnings("unused")  private Object field2;
  @SuppressWarnings("unused")  private Object field3;
  @SuppressWarnings("unused")  private Object field4;
  @SuppressWarnings("unused")  private Object field5;

  @SuppressWarnings("unused") @CompilationFinal private long[]   extensionPrimFields;
  @SuppressWarnings("unused") @CompilationFinal private Object[] extensionObjFields;

  private ObjectLayout objectLayout;
  private int    primitiveUsedMap;

  private final int numberOfFields;

  protected SObject(final SClass instanceClass) {
    numberOfFields = instanceClass.getNumberOfInstanceFields();
    clazz          = instanceClass;
    objectLayout   = instanceClass.getLayoutForInstances();
    assert objectLayout.getNumberOfFields() == numberOfFields;

    extensionPrimFields = getExtendedPrimStorage();
    extensionObjFields  = getExtendedObjectStorage();
  }

  protected SObject(final int numFields) {
    numberOfFields = numFields;
    objectLayout   = new ObjectLayout(numFields);

    extensionPrimFields = getExtendedPrimStorage();
    extensionObjFields  = getExtendedObjectStorage();
  }

  public int getNumberOfFields() {
    return numberOfFields;
  }

  public ObjectLayout getObjectLayout() {
    return objectLayout;
  }

  public long[] getExtendedPrimFields() {
    return extensionPrimFields;
  }

  public Object[] getExtensionObjFields() {
    return extensionObjFields;
  }

  public final void setClass(final SClass value) {
    transferToInterpreterAndInvalidate("SObject.setClass");
    // Set the class of this object by writing to the field with class index
    clazz = value;
    objectLayout = value.getLayoutForInstances();
    assert objectLayout.getNumberOfFields() == numberOfFields;
  }

  private long[] getExtendedPrimStorage() {
    return new long[objectLayout.getNumberOfUsedExtendedPrimStorageLocations()];
  }

  private Object[] getExtendedObjectStorage() {
    return new Object[objectLayout.getNumberOfUsedExtendedObjectStorageLocations()];
  }

  @ExplodeLoop
  private Object[] readAllFields() {
    CompilerAsserts.neverPartOfCompilation(); // at least I think so

    Object[] fieldValues = new Object[numberOfFields];
    for (int i = 0; i < numberOfFields; i++) {
      if (isFieldSet(i)) {
        fieldValues[i] = getField(i);
      } else {
        fieldValues[i] = null;
      }
    }
    return fieldValues;
  }

  @ExplodeLoop
  private void setAllFields(final Object[] fieldValues) {
    CompilerAsserts.neverPartOfCompilation();
    assert fieldValues.length == numberOfFields;

    for (int i = 0; i < numberOfFields; i++) {
      if (fieldValues[i] != null) {
        setField(i, fieldValues[i]);
      }
    }
  }

  public void updateLayoutToMatchClass() {
    ObjectLayout layoutAtClass = clazz.getLayoutForInstances();
    assert layoutAtClass.getNumberOfFields() == numberOfFields;
    if (objectLayout != layoutAtClass) {
      updateLayout(layoutAtClass);
    }
  }

  protected void updateLayout(final ObjectLayout layout) {
    CompilerAsserts.neverPartOfCompilation();

    assert objectLayout != layout;
    assert layout.getNumberOfFields() == numberOfFields;

    Object[] fieldValues = readAllFields();

    objectLayout        = layout;

    primitiveUsedMap    = 0;
    extensionPrimFields = getExtendedPrimStorage();
    extensionObjFields  = getExtendedObjectStorage();

    setAllFields(fieldValues);
  }

  @Override
  public final SClass getSOMClass(final Universe universe) {
    return clazz;
  }

  public static final SObject create(final SClass instanceClass) {
    return new SObject(instanceClass);
  }

  public static SObject create(final int numFields) {
    return new SObject(numFields);
  }

  private static final long FIRST_OBJECT_FIELD_OFFSET = getFirstObjectFieldOffset();
  private static final long FIRST_PRIM_FIELD_OFFSET   = getFirstPrimFieldOffset();
  private static final long OBJECT_FIELD_LENGTH = getObjectFieldLength();
  private static final long PRIM_FIELD_LENGTH   = getPrimFieldLength();

  public static long getObjectFieldOffset(final int fieldIndex) {
    assert 0 <= fieldIndex && fieldIndex < NUM_OBJECT_FIELDS;
    return FIRST_OBJECT_FIELD_OFFSET + fieldIndex * OBJECT_FIELD_LENGTH;
  }

  public static long getPrimitiveFieldOffset(final int fieldIndex) {
    assert 0 <= fieldIndex && fieldIndex < NUM_PRIMITIVE_FIELDS;
    return FIRST_PRIM_FIELD_OFFSET + fieldIndex * PRIM_FIELD_LENGTH;
  }

  public static int getPrimitiveFieldMask(final int fieldIndex) {
    assert 0 <= fieldIndex && fieldIndex < 32; // this limits the number of object fields for the moment...
    return 1 << fieldIndex;
  }

  public final boolean isPrimitiveSet(final int mask) {
    return (primitiveUsedMap & mask) != 0;
  }

  public final void markPrimAsSet(final int mask) {
    primitiveUsedMap |= mask;
  }

  private StorageLocation getLocation(final long index) {
    CompilerAsserts.neverPartOfCompilation();

    StorageLocation location = objectLayout.getStorageLocation(index);
    assert location != null;
    return location;
  }

  public final boolean isFieldSet(final long index) {
    StorageLocation location = getLocation(index);
    return location.isSet(this, true);
  }

  public final Object getField(final long index) {
    StorageLocation location = getLocation(index);
    return location.read(this, true);
  }

  public final void setField(final long index, final Object value) {
    StorageLocation location = getLocation(index);

    try {
      location.write(this, value);
    } catch (UninitalizedStorageLocationException e) {
      updateLayout(objectLayout.withInitializedField(index, value.getClass()));
      setFieldAfterLayoutChange(index, value);
    } catch (GeneralizeStorageLocationException e) {
      updateLayout(objectLayout.withGeneralizedField(index));
      setFieldAfterLayoutChange(index, value);
    }
  }

  private void setFieldAfterLayoutChange(final long index, final Object value) {
    StorageLocation location = getLocation(index);
    try {
      location.write(this, value);
    } catch (GeneralizeStorageLocationException
        | UninitalizedStorageLocationException e) {
      throw new RuntimeException("This should not happen, we just prepared this field for the new value.");
    }

    clazz.setLayoutForInstances(objectLayout);
  }

  private static long getFirstObjectFieldOffset() {
    try {
      final FieldOffsetProvider fieldOffsetProvider = getFieldOffsetProvider();

      final Field firstField = SObject.class.getDeclaredField("field1");
      return fieldOffsetProvider.objectFieldOffset(firstField);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static long getFirstPrimFieldOffset() {
    try {
      final FieldOffsetProvider fieldOffsetProvider = getFieldOffsetProvider();

      final Field firstField = SObject.class.getDeclaredField("primField1");
      return fieldOffsetProvider.objectFieldOffset(firstField);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static FieldOffsetProvider getFieldOffsetProvider()
      throws NoSuchFieldException, IllegalAccessException {
    final Field fieldOffsetProviderField =
        NodeUtil.class.getDeclaredField("unsafeFieldOffsetProvider");
    fieldOffsetProviderField.setAccessible(true);
    final FieldOffsetProvider fieldOffsetProvider =
        (FieldOffsetProvider) fieldOffsetProviderField.get(null);
    return fieldOffsetProvider;
  }

  private static long getObjectFieldLength() {
    try {
      return getFieldDistance("field1", "field2");
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static long getPrimFieldLength() {
    try {
      return getFieldDistance("primField1", "primField2");
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static long getFieldDistance(final String field1, final String field2) throws NoSuchFieldException,
      IllegalAccessException {
    final FieldOffsetProvider fieldOffsetProvider = getFieldOffsetProvider();

    final Field firstField  = SObject.class.getDeclaredField(field1);
    final Field secondField = SObject.class.getDeclaredField(field2);
    return fieldOffsetProvider.objectFieldOffset(secondField) - fieldOffsetProvider.objectFieldOffset(firstField);
  }
}
