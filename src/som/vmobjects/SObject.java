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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import org.graalvm.collections.Pair;
import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.nodes.dispatch.DispatchGuard;
import som.interpreter.objectstorage.ClassFactory;
import som.interpreter.objectstorage.ObjectLayout;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.interpreter.objectstorage.StorageLocation;
import som.interpreter.objectstorage.StorageLocation.DoubleStorageLocation;
import som.interpreter.objectstorage.StorageLocation.LongStorageLocation;
import som.interpreter.objectstorage.StorageLocation.ObjectStorageLocation;
import som.interpreter.objectstorage.StorageLocation.UnwrittenStorageLocation;
import som.vm.constants.Nil;


public abstract class SObject extends SObjectWithClass {

  public static final int NUM_PRIMITIVE_FIELDS = 5;
  public static final int NUM_OBJECT_FIELDS    = 5;

  // TODO: when we got the possibility that we can hint to the compiler that a
  // read is from a final field, we should remove this
  public static final class SImmutableObject extends SObject {

    public SImmutableObject(final SClass instanceClass, final ClassFactory classGroup,
        final ObjectLayout layout) {
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
      this.isValue = old.isValue;
    }

    @CompilationFinal public long primField1;
    @CompilationFinal public long primField2;
    @CompilationFinal public long primField3;
    @CompilationFinal public long primField4;
    @CompilationFinal public long primField5;

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
    public long primField1;
    public long primField2;
    public long primField3;
    public long primField4;
    public long primField5;

    public Object field1;
    public Object field2;
    public Object field3;
    public Object field4;
    public Object field5;

    // this field exists because HotSpot reorders fields, and we need to keep
    // the layouts in sync to avoid having to manage different offsets for
    // SMutableObject and SImmuableObject
    @SuppressWarnings("unused") private boolean isValueOfSImmutableObjectSync;

    public SMutableObject(final SClass instanceClass, final ClassFactory factory,
        final ObjectLayout layout) {
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
      field1 = field2 = field3 = field4 = field5 = null;
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
      copy.clazz = clazz;

      copy.objectLayout = objectLayout;
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
      // iff we want to ignore class slot stuff...
      // might be easier to just handle those
      return o.primField1 == primField1 &&
          o.primField2 == primField2 &&
          o.primField3 == primField3 &&
          o.primField4 == primField4 &&
          o.primField5 == primField5 &&

          o.classGroup == classGroup && // TODO: should not be necessary
          o.clazz == clazz && // TODO: should not be necessary

          o.primitiveUsedMap == primitiveUsedMap && // TODO: necessary?
          Arrays.equals(o.extensionPrimFields, extensionPrimFields) &&

          txMutObjLocEquals(o);
    }

    private boolean txMutObjLocEquals(final SMutableObject o) {
      EconomicMap<SlotDefinition, StorageLocation> oLocs =
          o.objectLayout.getStorageLocations();
      EconomicMap<SlotDefinition, StorageLocation> locs = objectLayout.getStorageLocations();

      MapCursor<SlotDefinition, StorageLocation> e = locs.getEntries();
      while (e.advance()) {
        // need to ignore mutators and class slots
        // ignore primitives, have been checked separately before
        if (e.getKey().getClass() == SlotDefinition.class && e.getValue().isObjectLocation() &&
            e.getValue().read(this) != oLocs.get(e.getKey()).read(this)) {
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

      classGroup = wc.classGroup; // TODO: should not be necessary
      clazz = wc.clazz; // TODO: should not be necessary

      objectLayout = wc.objectLayout;
      primitiveUsedMap = wc.primitiveUsedMap;
      extensionPrimFields = wc.extensionPrimFields;

      txSetMutObjLoc(wc);
    }

    /** Only set the mutable slots. */
    private void txSetMutObjLoc(final SMutableObject wc) {
      EconomicMap<SlotDefinition, StorageLocation> oLocs =
          wc.objectLayout.getStorageLocations();

      MapCursor<SlotDefinition, StorageLocation> e = oLocs.getEntries();
      while (e.advance()) {
        // need to ignore mutators and class slots
        if (e.getKey().getClass() == SlotDefinition.class) {
          Object val = e.getValue().read(wc);
          this.writeSlot(e.getKey(), val);
        }
      }
    }
  }

  // TODO: if there is the possibility that we can hint that a read is from a
  // final field, we should reconsider removing these and store them in
  // normal object fields
  @CompilationFinal(dimensions = 0) protected long[]   extensionPrimFields;
  @CompilationFinal(dimensions = 0) protected Object[] extensionObjFields;

  // we manage the layout entirely in the class, but need to keep a copy here
  // to know in case the layout changed that we can update the instances lazily
  @CompilationFinal protected ObjectLayout objectLayout;
  public int                               primitiveUsedMap;

  public SObject(final SClass instanceClass, final ClassFactory factory,
      final ObjectLayout layout) {
    super(instanceClass, factory);
    assert factory.getInstanceLayout() == layout
        || layout.layoutForSameClasses(factory.getInstanceLayout());
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

    // TODO: these tests should be compilation constant based on the object layout, check
    // whether this needs to be optimized
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
   *         values, object layout etc. Object fields are not cloned. No deep copying
   *         either. This method is used for cloning transfer objects.
   */
  public abstract SObject cloneBasics();

  private void setLayoutInitially(final ObjectLayout layout) {
    CompilerAsserts.partialEvaluationConstant(layout);
    objectLayout = layout;
    extensionPrimFields = getExtendedPrimStorage(layout);
    extensionObjFields = getExtendedObjectStorage(layout);
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
    CompilerAsserts.neverPartOfCompilation(
        "Only meant to be used in object system initalization");
    super.setClass(value);
    setLayoutInitially(value.getLayoutForInstancesUnsafe());
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

  @ExplodeLoop
  private EconomicMap<SlotDefinition, Object> getAllFields() {
    assert objectLayout != null;

    EconomicMap<SlotDefinition, StorageLocation> locations =
        objectLayout.getStorageLocations();
    EconomicMap<SlotDefinition, Object> fieldValues =
        EconomicMap.create((int) (locations.size() / 0.75f));

    MapCursor<SlotDefinition, StorageLocation> loc = locations.getEntries();
    while (loc.advance()) {
      if (loc.getValue().isSet(this)) {
        fieldValues.put(loc.getKey(), loc.getValue().read(this));
      } else {
        fieldValues.put(loc.getKey(), null);
      }
    }
    return fieldValues;
  }

  protected abstract void resetFields();

  @ExplodeLoop
  private void setAllFields(final EconomicMap<SlotDefinition, Object> fieldValues) {
    resetFields();
    primitiveUsedMap = 0;
    MapCursor<SlotDefinition, Object> entry = fieldValues.getEntries();
    while (entry.advance()) {
      if (entry.getValue() != null) {
        writeSlot(entry.getKey(), entry.getValue());
      } else if (getLocation(entry.getKey()) instanceof ObjectStorageLocation) {
        writeSlot(entry.getKey(), Nil.nilObject);
      }
    }
  }

  public final boolean isLayoutCurrent() {
    return objectLayout == clazz.getLayoutForInstancesUnsafe() && objectLayout.isValid();
  }

  public final synchronized boolean updateLayoutToMatchClass() {
    ObjectLayout layoutAtClass = clazz.getLayoutForInstancesToUpdateObject();

    if (objectLayout != layoutAtClass) {
      setLayoutAndTransferFields(layoutAtClass);
      return true;
    } else {
      return false;
    }
  }

  private void setLayoutAndTransferFields(final ObjectLayout layoutAtClass) {
    CompilerDirectives.transferToInterpreterAndInvalidate();

    EconomicMap<SlotDefinition, Object> fieldValues = getAllFields();
   //fieldValues = fixFieldsToNewSlots(layoutAtClass, fieldValues);
    ObjectLayout oldObjectLayout = objectLayout;
    objectLayout = layoutAtClass;
    assert oldObjectLayout != objectLayout;
    extensionPrimFields = getExtendedPrimStorage(layoutAtClass);
    extensionObjFields = getExtendedObjectStorage(layoutAtClass);
    try {
    setAllFields(fieldValues);
    } catch ( ArithmeticException error){
      objectLayout = oldObjectLayout;
      this.setLayoutAndTransferFieldsOnClassUpdate(layoutAtClass, fieldValues);
    }
  }

  private void setLayoutAndTransferFieldsOnClassUpdate(final ObjectLayout layoutAtClass,EconomicMap<SlotDefinition, Object> fieldValues) {
    CompilerDirectives.transferToInterpreterAndInvalidate();
    EconomicMap<SlotDefinition, Object> newSlots = fixFieldsToNewSlots(layoutAtClass, fieldValues);
    objectLayout = layoutAtClass;
    extensionPrimFields = getExtendedPrimStorage(layoutAtClass);
    extensionObjFields = getExtendedObjectStorage(layoutAtClass);

    setAllFields(newSlots);
    DispatchGuard.invalidateAssumption();
  }

  private EconomicMap<SlotDefinition, Object> fixFieldsToNewSlots(ObjectLayout layoutAtClass, EconomicMap<SlotDefinition, Object> fieldValues ) {
    EconomicMap<SlotDefinition, StorageLocation> oldSlots = objectLayout.getStorageLocations();
    EconomicMap<SlotDefinition, StorageLocation> newSlots = layoutAtClass.getStorageLocations();
    Map<SlotDefinition,Pair<SlotDefinition,Object>> layoutLocations = new HashMap<>();
    EconomicMap<SlotDefinition,Object> newFieldValues = EconomicMap.create();
    for (SlotDefinition newSlot : newSlots.getKeys()){
      for (SlotDefinition oldSlot : oldSlots.getKeys()){
        if(oldSlot.getName() == newSlot.getName()){
          StorageLocation newLoc = newSlots.get(newSlot);
          if (newLoc.isObjectLocation()){
            layoutLocations.put(oldSlot,  Pair.create(newSlot,fieldValues.get(oldSlot)));
            break;
          }
        }
      }

    }
    for (SlotDefinition slot : fieldValues.getKeys()) {
      //StorageLocation location = oldSlots.get(slot);
      Pair<SlotDefinition,Object> newSlotDef = layoutLocations.get(slot);
      if (newSlotDef != null) {
        newFieldValues.put(newSlotDef.getLeft(),newSlotDef.getRight());
      }
    }
    return newFieldValues;
  }

  /**
   * Since this operation is racy with other initializations of the field, we
   * first check whether the slot is unwritten. If that is the case, the slot
   * is initialized. Otherwise, we check whether the field needs to be
   * generalized.
   *
   * <p>
   * <strong>Note:</strong> This method is expected to be called while
   * holding a lock on <code>this</code>.
   */
  protected final void updateLayoutWithInitializedField(
      final SlotDefinition slot, final Class<?> type) {
    StorageLocation loc = objectLayout.getStorageLocation(slot);
    if (loc instanceof UnwrittenStorageLocation) {
      ObjectLayout layout = classGroup.updateInstanceLayoutWithInitializedField(slot, type);
      assert objectLayout != layout;
      setLayoutAndTransferFields(layout);
    } else if ((type == Long.class && !(loc instanceof LongStorageLocation)) ||
        (type == Double.class && !(loc instanceof DoubleStorageLocation))) {
      updateLayoutWithGeneralizedField(slot);
    }
  }

  /**
   * Since this operation is racy with other generalizations of the field, we
   * first check whether the slot is not already using an
   * {@link ObjectStorageLocation}. If it is using one, another generalization
   * happened already, and we don't need to do it anymore.
   *
   * <p>
   * <strong>Note:</strong> This method is expected to be called while
   * holding a lock on <code>this</code>.
   */
  protected final void updateLayoutWithGeneralizedField(final SlotDefinition slot) {
    StorageLocation loc = objectLayout.getStorageLocation(slot);
    if (!(loc instanceof ObjectStorageLocation)) {
      ObjectLayout layout = classGroup.updateInstanceLayoutWithGeneralizedField(slot);

      assert objectLayout != layout;
      setLayoutAndTransferFields(layout);
    }
  }

  public static int getPrimitiveFieldMask(final int fieldIndex) {
    assert 0 <= fieldIndex && fieldIndex < 32; // this limits the number of object fields for
                                               // the moment...
    return 1 << fieldIndex;
  }

  private StorageLocation getLocation(final SlotDefinition slot) {
    StorageLocation location = objectLayout.getStorageLocation(slot);
    // TODO: added here a division by 0 to be able to debug this when it happens.
    // I do not recall what it is due to, but probably it is about reloading nested classes
    // assert location != null;
    if (location == null) {
      int a = 1 / 0;
    }
    return location;
  }

  public final Object readSlot(final SlotDefinition slot) {
    CompilerAsserts.neverPartOfCompilation("getField");
    StorageLocation location = getLocation(slot);
    return location.read(this);
  }

  public final synchronized void writeUninitializedSlot(final SlotDefinition slot,
      final Object value) {
    updateLayoutWithInitializedField(slot, value.getClass());
    setFieldAfterLayoutChange(slot, value);
  }

  public final synchronized void writeAndGeneralizeSlot(final SlotDefinition slot,
      final Object value) {
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

  public final synchronized void ensureSlotAllocatedToAvoidDeadlock(
      final SlotDefinition slot) {
    StorageLocation loc = objectLayout.getStorageLocation(slot);
    if (!(loc instanceof ObjectStorageLocation)) {
      ObjectLayout layout =
          classGroup.updateInstanceLayoutWithGeneralizedField(slot);

      assert objectLayout != layout;
      setLayoutAndTransferFields(layout);
    }
  }
}
