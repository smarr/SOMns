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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.IntValueProfile;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.objectstorage.ClassFactory;
import som.interpreter.objectstorage.ObjectLayout;
import som.interpreter.objectstorage.StorageLocation;
import som.interpreter.objectstorage.StorageLocation.AbstractObjectStorageLocation;
import som.vm.constants.Nil;

public abstract class SObject extends SObjectWithClass {

  public static final int NUM_PRIMITIVE_FIELDS = 5;
  public static final int NUM_OBJECT_FIELDS    = 5;

  // TODO: when we got the possibility that we can hint to the compiler that a
  //       read is from a final field, we should remove this
  public static final class SImmutableObject extends SObject {

    public SImmutableObject(final SClass instanceClass, final ClassFactory classGroup, final ObjectLayout layout) {
      super(instanceClass, classGroup, layout);
      field1 = field2 = field3 = field4 = field5 = Nil.nilObject;
      isValue = instanceClass.declaredAsValue();
    }

    public SImmutableObject(final boolean incompleteDefinition,
        final boolean isKernelObj) {
      super(incompleteDefinition);
      assert isKernelObj;
      isValue = true;
    }

    /**
     * Copy constructor.
     */
    private SImmutableObject(final SImmutableObject old) {
      super(old);
      this.primField1 = old.primField1;
      this.primField2 = old.primField2;
      this.primField3 = old.primField3;
      this.primField4 = old.primField4;
      this.primField5 = old.primField5;
      this.isValue    = old.isValue;
    }

    @CompilationFinal protected long   primField1;
    @CompilationFinal protected long   primField2;
    @CompilationFinal protected long   primField3;
    @CompilationFinal protected long   primField4;
    @CompilationFinal protected long   primField5;

    @CompilationFinal public Object field1;
    @CompilationFinal public Object field2;
    @CompilationFinal public Object field3;
    @CompilationFinal public Object field4;
    @CompilationFinal public Object field5;

    @CompilationFinal protected boolean isValue;

    @Override
    protected void resetFields() {
      field1 = field2 = field3 = field4 = field5 = null;
      primField1 = primField2 = primField3 = primField4 = primField5 = Long.MIN_VALUE;
    }

    @Override
    public boolean isValue() {
      return isValue;
    }

    @Override
    public SObject cloneBasics() {
      assert !isValue : "There should not be any need to clone a value";
      return new SImmutableObject(this);
    }
  }

  public static final class SMutableObject extends SObject {
    private long   primField1;
    private long   primField2;
    private long   primField3;
    private long   primField4;
    private long   primField5;

    private Object field1;
    private Object field2;
    private Object field3;
    private Object field4;
    private Object field5;

    // this field exists because HotSpot reorders fields, and we need to keep
    // the layouts in sync to avoid having to manage different offsets for
    // SMutableObject and SImmuableObject
    @SuppressWarnings("unused") private boolean isValueOfSImmutableObjectSync;

    public SMutableObject(final SClass instanceClass, final ClassFactory factory, final ObjectLayout layout) {
      super(instanceClass, factory, layout);
      field1 = field2 = field3 = field4 = field5 = Nil.nilObject;
    }

    public SMutableObject(final boolean incompleteDefinition) {
      super(incompleteDefinition);
    }

    protected SMutableObject(final SMutableObject old) {
      super(old);
      this.primField1 = old.primField1;
      this.primField2 = old.primField2;
      this.primField3 = old.primField3;
      this.primField4 = old.primField4;
      this.primField5 = old.primField5;
    }

    @Override
    protected void resetFields() {
      field1     = field2     = field3     = field4     = field5     = null;
      primField1 = primField2 = primField3 = primField4 = primField5 = Long.MIN_VALUE;
    }

    @Override
    public boolean isValue() {
      return false;
    }

    @Override
    public SObject cloneBasics() {
      return new SMutableObject(this);
    }

    public SMutableObject shallowCopy() {
      SMutableObject copy = new SMutableObject(true);
      copy.primField1 = primField1;
      copy.primField2 = primField2;
      copy.primField3 = primField3;
      copy.primField4 = primField4;
      copy.primField5 = primField5;

      copy.classGroup = classGroup;
      copy.clazz      = clazz;

      copy.objectLayout     = objectLayout;
      copy.primitiveUsedMap = primitiveUsedMap;

      copy.field1 = field1;
      copy.field2 = field2;
      copy.field3 = field3;
      copy.field4 = field4;
      copy.field5 = field5;

      if (extensionPrimFields != null) {
        copy.extensionPrimFields = extensionPrimFields.clone();
      }

      if (extensionObjFields != null) {
        copy.extensionObjFields = extensionObjFields.clone();
      }

      return copy;
    }

    public boolean txEquals(final SMutableObject o) {
      // TODO: we actually need to take the object layout into account,
      //       iff we want to ignore class slot stuff...
      //       might be easier to just handle those
      return
          o.primField1 == primField1 &&
          o.primField2 == primField2 &&
          o.primField3 == primField3 &&
          o.primField4 == primField4 &&
          o.primField5 == primField5 &&

          o.classGroup == classGroup && // TODO: should not be necessary
          o.clazz      == clazz      && // TODO: should not be necessary

          o.primitiveUsedMap == primitiveUsedMap && // TODO: necessary?
          Arrays.equals(o.extensionPrimFields, extensionPrimFields) &&

          txMutObjLocEquals(o);
    }

    private boolean txMutObjLocEquals(final SMutableObject o) {
      HashMap<SlotDefinition, StorageLocation> oLocs = o.objectLayout.getStorageLocations();
      HashMap<SlotDefinition, StorageLocation> locs  = objectLayout.getStorageLocations();

      for (Entry<SlotDefinition, StorageLocation> e : locs.entrySet()) {
        // need to ignore mutators and class slots
        if (e.getKey().getClass() == SlotDefinition.class &&
            e.getValue().read(this) != oLocs.get(e.getKey()).read(o)) {
          return false;
        }
      }
      return true;
    }

    public void txSet(final SMutableObject wc) {
      primField1 = wc.primField1;
      primField2 = wc.primField2;
      primField3 = wc.primField3;
      primField4 = wc.primField4;
      primField5 = wc.primField5;

      classGroup = wc.classGroup;  // TODO: should not be necessary
      clazz      = wc.clazz;       // TODO: should not be necessary

      objectLayout = wc.objectLayout;
      primitiveUsedMap = wc.primitiveUsedMap;
      extensionPrimFields = wc.extensionPrimFields;

      txSetMutObjLoc(wc);
    }

    /** Only set the mutable slots. */
    private void txSetMutObjLoc(final SMutableObject wc) {
      HashMap<SlotDefinition, StorageLocation> oLocs = wc.objectLayout.getStorageLocations();

      for (Entry<SlotDefinition, StorageLocation> e : oLocs.entrySet()) {
        // need to ignore mutators and class slots
        if (e.getKey().getClass() == SlotDefinition.class) {
          Object val = e.getValue().read(wc);
          this.writeSlot(e.getKey(), val);
        }
      }
    }
  }


  // TODO: if there is the possibility that we can hint that a read is from a
  //       final field, we should reconsider removing these and store them in
  //       normal object fields
  @CompilationFinal(dimensions = 0) protected long[]   extensionPrimFields;
  @CompilationFinal(dimensions = 0) protected Object[] extensionObjFields;

  // we manage the layout entirely in the class, but need to keep a copy here
  // to know in case the layout changed that we can update the instances lazily
  @CompilationFinal protected ObjectLayout objectLayout;
  protected int primitiveUsedMap;

  public SObject(final SClass instanceClass, final ClassFactory factory, final ObjectLayout layout) {
    super(instanceClass, factory);
    assert factory.getInstanceLayout() == layout || layout.layoutForSameClasses(factory.getInstanceLayout());
    setLayoutInitially(layout);
  }

  public SObject(final boolean incompleteDefinition) {
    assert incompleteDefinition; // used during bootstrap
  }

  /** Copy Constructor. */
  protected SObject(final SObject old) {
    super(old);
    this.objectLayout = old.objectLayout;

    this.primitiveUsedMap = old.primitiveUsedMap;

    // TODO: these tests should be compilation constant based on the object layout, check whether this needs to be optimized
    // we copy the content here, because we know they are all values
    if (old.extensionPrimFields != null) {
      this.extensionPrimFields = old.extensionPrimFields.clone();
    }

    // do not want to copy the content for the obj extension array, because
    // transfer should handle each of them.
    if (old.extensionObjFields != null) {
      this.extensionObjFields = new Object[old.extensionObjFields.length];
    }
  }

  /**
   * @return new object of the same type, initialized with same primitive
   * values, object layout etc. Object fields are not cloned. No deep copying
   * either. This method is used for cloning transfer objects.
   */
  public abstract SObject cloneBasics();

  public boolean isPrimitiveSet(final int mask) {
    return (primitiveUsedMap & mask) != 0;
  }

  public boolean isPrimitiveSet(final int mask, final IntValueProfile markProfile) {
    return (markProfile.profile(primitiveUsedMap) & mask) != 0;
  }

  public void markPrimAsSet(final int mask) {
    primitiveUsedMap |= mask;
  }

  private void setLayoutInitially(final ObjectLayout layout) {
    CompilerAsserts.partialEvaluationConstant(layout);
    objectLayout        = layout;
    extensionPrimFields = getExtendedPrimStorage(layout);
    extensionObjFields  = getExtendedObjectStorage(layout);
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
    CompilerAsserts.neverPartOfCompilation("Only meant to be used in object system initalization");
    super.setClass(value);
    setLayoutInitially(value.getLayoutForInstances());
  }

  private long[] getExtendedPrimStorage(final ObjectLayout layout) {
    int numExtFields = layout.getNumberOfUsedExtendedPrimStorageLocations();
    CompilerAsserts.partialEvaluationConstant(numExtFields);
    if (numExtFields == 0) {
      return null;
    } else {
      return new long[numExtFields];
    }
  }

  private Object[] getExtendedObjectStorage(final ObjectLayout layout) {
    int numExtFields = layout.getNumberOfUsedExtendedObjectStorageLocations();
    CompilerAsserts.partialEvaluationConstant(numExtFields);
    if (numExtFields == 0) {
      return null;
    }

    Object[] storage = new Object[numExtFields];
    Arrays.fill(storage, Nil.nilObject);
    return storage;
  }

  private static final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

  @ExplodeLoop
  private HashMap<SlotDefinition, Object> getAllFields() {
    assert objectLayout != null;

    HashMap<SlotDefinition, StorageLocation> locations = objectLayout.getStorageLocations();
    HashMap<SlotDefinition, Object> fieldValues = new HashMap<>((int) (locations.size() / 0.75f));

    for (Entry<SlotDefinition, StorageLocation> loc : locations.entrySet()) {
      if (loc.getValue().isSet(this, primMarkProfile)) {
        fieldValues.put(loc.getKey(), loc.getValue().read(this));
      } else {
        fieldValues.put(loc.getKey(), null);
      }
    }
    return fieldValues;
  }

  protected abstract void resetFields();

  @ExplodeLoop
  private void setAllFields(final HashMap<SlotDefinition, Object> fieldValues) {
    resetFields();
    primitiveUsedMap = 0;

    for (Entry<SlotDefinition, Object> entry : fieldValues.entrySet()) {
      if (entry.getValue() != null) {
        writeSlot(entry.getKey(), entry.getValue());
      } else if (getLocation(entry.getKey()) instanceof AbstractObjectStorageLocation) {
        writeSlot(entry.getKey(), Nil.nilObject);
      }
    }
  }

  public final boolean isLayoutCurrent() {
    return objectLayout == clazz.getLayoutForInstances();
  }

  public final synchronized boolean updateLayoutToMatchClass() {
    ObjectLayout layoutAtClass = clazz.getLayoutForInstances();

    if (objectLayout != layoutAtClass) {
      setLayoutAndTransferFields();
      return true;
    } else {
      return false;
    }
  }

  private void setLayoutAndTransferFields() {
    CompilerDirectives.transferToInterpreterAndInvalidate();

    ObjectLayout layoutAtClass;
    synchronized (clazz) {
      layoutAtClass = clazz.getLayoutForInstances();
      if (objectLayout == layoutAtClass) {
        return;
      }
    }

    HashMap<SlotDefinition, Object> fieldValues = getAllFields();

    objectLayout        = layoutAtClass;
    extensionPrimFields = getExtendedPrimStorage(layoutAtClass);
    extensionObjFields  = getExtendedObjectStorage(layoutAtClass);

    setAllFields(fieldValues);
  }

  protected final void updateLayoutWithInitializedField(final SlotDefinition slot, final Class<?> type) {
    // TODO: this method does not yet support two threads arriving at the given slot/object at the same time
    //       the second thread likely needs to start over completely again
    ObjectLayout layout = classGroup.updateInstanceLayoutWithInitializedField(slot, type);
    assert objectLayout != layout;
    setLayoutAndTransferFields();
  }

  protected final void updateLayoutWithGeneralizedField(final SlotDefinition slot) {
    ObjectLayout layout = classGroup.updateInstanceLayoutWithGeneralizedField(slot);

    assert objectLayout != layout;
    setLayoutAndTransferFields();
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

  private StorageLocation getLocation(final SlotDefinition slot) {
    StorageLocation location = objectLayout.getStorageLocation(slot);
    assert location != null;
    return location;
  }

  public final Object readSlot(final SlotDefinition slot) {
    CompilerAsserts.neverPartOfCompilation("getField");
    StorageLocation location = getLocation(slot);
    return location.read(this);
  }

  public final synchronized void writeUninitializedSlot(final SlotDefinition slot, final Object value) {
    updateLayoutWithInitializedField(slot, value.getClass());
    setFieldAfterLayoutChange(slot, value);
  }

  public final synchronized void writeAndGeneralizeSlot(final SlotDefinition slot, final Object value) {
    updateLayoutWithGeneralizedField(slot);
    setFieldAfterLayoutChange(slot, value);
  }

  public final void writeSlot(final SlotDefinition slot, final Object value) {
    CompilerAsserts.neverPartOfCompilation("setField");
    StorageLocation location = getLocation(slot);
    location.write(this, value);
  }

  private void setFieldAfterLayoutChange(final SlotDefinition slot,
      final Object value) {
    CompilerAsserts.neverPartOfCompilation("SObject.setFieldAfterLayoutChange(..)");

    StorageLocation location = getLocation(slot);
    location.write(this, value);
  }

  private static long getFirstObjectFieldOffset() {
    CompilerAsserts.neverPartOfCompilation("SObject.getFirstObjectFieldOffset()");
    try {
      final Field firstField = SMutableObject.class.getDeclaredField("field1");
      return StorageLocation.getFieldOffset(firstField);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private static long getFirstPrimFieldOffset() {
    CompilerAsserts.neverPartOfCompilation("SObject.getFirstPrimFieldOffset()");
    try {
      final Field firstField = SMutableObject.class.getDeclaredField("primField1");
      return StorageLocation.getFieldOffset(firstField);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private static long getObjectFieldLength() {
    CompilerAsserts.neverPartOfCompilation("getObjectFieldLength()");

    try {
      long dist = getFieldDistance("field1", "field2");
      // this can go wrong if the VM rearranges fields to fill holes in the
      // memory layout of the object structure
      assert dist == 4 || dist == 8 : "We expect these fields to be adjecent and either 32 or 64bit appart.";
      return dist;
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static long getPrimFieldLength() {
    CompilerAsserts.neverPartOfCompilation("getPrimFieldLength()");

    try {
      long dist = getFieldDistance("primField1", "primField2");
      // this can go wrong if the VM rearranges fields to fill holes in the
      // memory layout of the object structure
      assert dist == 8 : "We expect these fields to be adjecent and 64bit appart.";
      return dist;
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static long getFieldDistance(final String field1, final String field2) throws NoSuchFieldException,
      IllegalAccessException {
    final Field firstField  = SMutableObject.class.getDeclaredField(field1);
    final Field secondField = SMutableObject.class.getDeclaredField(field2);
    return StorageLocation.getFieldOffset(secondField) - StorageLocation.getFieldOffset(firstField);
  }
}
