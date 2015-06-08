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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;

import som.compiler.ClassDefinition.SlotDefinition;
import som.interpreter.objectstorage.ObjectLayout;
import som.interpreter.objectstorage.StorageLocation;
import som.interpreter.objectstorage.StorageLocation.AbstractObjectStorageLocation;
import som.interpreter.objectstorage.StorageLocation.GeneralizeStorageLocationException;
import som.interpreter.objectstorage.StorageLocation.UninitalizedStorageLocationException;
import som.vm.Bootstrap;
import som.vm.constants.Nil;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeFieldAccessor;
import com.oracle.truffle.api.nodes.NodeUtil.FieldOffsetProvider;

public class SObject extends SObjectWithoutFields {

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

  // we manage the layout entirely in the class, but need to keep a copy here
  // to know in case the layout changed that we can update the instances lazily
  @CompilationFinal private ObjectLayout objectLayout;

  private int primitiveUsedMap;

  private int numberOfFields;

  public SObject(final SClass instanceClass) {
    super(instanceClass);
    numberOfFields = instanceClass.getNumberOfInstanceFields();
    setLayoutInitially(instanceClass.getLayoutForInstances());
  }

  public SObject(final boolean incompleteDefinition) {
    assert incompleteDefinition; // used during bootstrap
    numberOfFields = -1;
  }

  private void setLayoutInitially(final ObjectLayout layout) {
    field1 = field2 = field3 = field4 = field5 = Nil.nilObject;

    objectLayout   = layout;
    assert objectLayout.getNumberOfFields() == numberOfFields || !Bootstrap.isObjectSystemInitialized();
    numberOfFields = objectLayout.getNumberOfFields();

    extensionPrimFields = getExtendedPrimStorage();
    extensionObjFields  = getExtendedObjectStorage();
  }

  public final int getNumberOfFields() {
    return numberOfFields;
  }

  public final ObjectLayout getObjectLayout() {
    // TODO: should I really remove it, or should I update the layout?
    // assert clazz.getLayoutForInstances() == objectLayout;
    return objectLayout;
  }

  public final long[] getExtendedPrimFields() {
    return extensionPrimFields;
  }

  public final Object[] getExtensionObjFields() {
    return extensionObjFields;
  }

  @Override
  public final void setClass(final SClass value) {
    super.setClass(value);
    setLayoutInitially(value.getLayoutForInstances());
  }

  private long[] getExtendedPrimStorage() {
    return new long[objectLayout.getNumberOfUsedExtendedPrimStorageLocations()];
  }

  private Object[] getExtendedObjectStorage() {
    Object[] storage = new Object[objectLayout.getNumberOfUsedExtendedObjectStorageLocations()];
    Arrays.fill(storage, Nil.nilObject);
    return storage;
  }

  @ExplodeLoop
  private HashMap<SlotDefinition, Object> getAllFields() {
    assert objectLayout != null;

    HashMap<SlotDefinition, Object> fieldValues = new HashMap<>((int) (numberOfFields / 0.75f));
    HashMap<SlotDefinition, StorageLocation> locations = objectLayout.getStorageLocations();

    for (Entry<SlotDefinition, StorageLocation> loc : locations.entrySet()) {
      if (loc.getValue().isSet(this, true)) {
        fieldValues.put(loc.getKey(), loc.getValue().read(this, true));
      } else {
        fieldValues.put(loc.getKey(), null);
      }
    }
    return fieldValues;
  }

  @ExplodeLoop
  private void setAllFields(final HashMap<SlotDefinition, Object> fieldValues) {
    field1 = field2 = field3 = field4 = field5 = null;
    primField1 = primField2 = primField3 = primField4 = primField5 = Long.MIN_VALUE;

    assert fieldValues.size() == numberOfFields;

    for (Entry<SlotDefinition, Object> entry : fieldValues.entrySet()) {
      if (entry.getValue() != null) {
        setField(entry.getKey(), entry.getValue());
      } else if (getLocation(entry.getKey()) instanceof AbstractObjectStorageLocation) {
        setField(entry.getKey(), Nil.nilObject);
      }
    }
  }

  public final boolean updateLayoutToMatchClass() {
    ObjectLayout layoutAtClass = clazz.getLayoutForInstances();
    assert layoutAtClass.getNumberOfFields() == numberOfFields;

    if (objectLayout != layoutAtClass) {
      setLayoutAndTransferFields(layoutAtClass);
      return true;
    } else {
      return false;
    }
  }

  private void setLayoutAndTransferFields(final ObjectLayout layout) {
    CompilerDirectives.transferToInterpreterAndInvalidate();

    HashMap<SlotDefinition, Object> fieldValues = getAllFields();

    objectLayout        = layout;

    primitiveUsedMap    = 0;
    extensionPrimFields = getExtendedPrimStorage();
    extensionObjFields  = getExtendedObjectStorage();

    setAllFields(fieldValues);
  }

  protected final void updateLayoutWithInitializedField(final SlotDefinition slot, final Class<?> type) {
    ObjectLayout layout = clazz.updateInstanceLayoutWithInitializedField(slot, type);

    assert objectLayout != layout;
    assert layout.getNumberOfFields() == numberOfFields;

    setLayoutAndTransferFields(layout);
  }

  protected final void updateLayoutWithGeneralizedField(final SlotDefinition slot) {
    ObjectLayout layout = clazz.updateInstanceLayoutWithGeneralizedField(slot);

    assert objectLayout != layout;
    assert layout.getNumberOfFields() == numberOfFields;

    setLayoutAndTransferFields(layout);
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

  private StorageLocation getLocation(final SlotDefinition slot) {
    StorageLocation location = objectLayout.getStorageLocation(slot);
    assert location != null;
    return location;
  }

  public final Object getField(final SlotDefinition slot) {
    CompilerAsserts.neverPartOfCompilation("getField");
    StorageLocation location = getLocation(slot);
    return location.read(this, true);
  }

  public final void setField(final SlotDefinition slot, final Object value) {
    CompilerAsserts.neverPartOfCompilation("setField");
    StorageLocation location = getLocation(slot);

    try {
      location.write(this, value);
    } catch (UninitalizedStorageLocationException e) {
      updateLayoutWithInitializedField(slot, value.getClass());
      setFieldAfterLayoutChange(slot, value);
    } catch (GeneralizeStorageLocationException e) {
      updateLayoutWithGeneralizedField(slot);
      setFieldAfterLayoutChange(slot, value);
    }
  }

  private void setFieldAfterLayoutChange(final SlotDefinition slot,
      final Object value) {
    CompilerAsserts.neverPartOfCompilation("SObject.setFieldAfterLayoutChange(..)");

    StorageLocation location = getLocation(slot);
    try {
      location.write(this, value);
    } catch (GeneralizeStorageLocationException
        | UninitalizedStorageLocationException e) {
      throw new RuntimeException("This should not happen, we just prepared this field for the new value.");
    }
  }

  private static long getFirstObjectFieldOffset() {
    CompilerAsserts.neverPartOfCompilation("SObject.getFirstObjectFieldOffset()");
    try {
      final FieldOffsetProvider fieldOffsetProvider = getFieldOffsetProvider();

      final Field firstField = SObject.class.getDeclaredField("field1");
      return fieldOffsetProvider.objectFieldOffset(firstField);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static long getFirstPrimFieldOffset() {
    CompilerAsserts.neverPartOfCompilation("SObject.getFirstPrimFieldOffset()");
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
        NodeFieldAccessor.class.getDeclaredField("unsafeFieldOffsetProvider");
    fieldOffsetProviderField.setAccessible(true);
    final FieldOffsetProvider fieldOffsetProvider =
        (FieldOffsetProvider) fieldOffsetProviderField.get(null);
    return fieldOffsetProvider;
  }

  private static long getObjectFieldLength() {
    CompilerAsserts.neverPartOfCompilation("getObjectFieldLength()");

    try {
      return getFieldDistance("field1", "field2");
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static long getPrimFieldLength() {
    CompilerAsserts.neverPartOfCompilation("getPrimFieldLength()");

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
