package som.interpreter.objectstorage;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.objectstorage.StorageLocation.DoubleStorageLocation;
import som.interpreter.objectstorage.StorageLocation.LongStorageLocation;
import som.interpreter.objectstorage.StorageLocation.ObjectStorageLocation;
import som.interpreter.objectstorage.StorageLocation.UnwrittenStorageLocation;
import som.vm.NotYetImplementedException;
import som.vmobjects.SObject;


public final class ObjectLayout {
  private final ClassFactory forClasses;
  private final Assumption   latestLayoutForClass;

  private final int     primitiveStorageLocationsUsed;
  private final int     objectStorageLocationsUsed;
  private final int     totalNumberOfStorageLocations;
  private final boolean onlyImmutableFields;
  private final boolean isTransferObject;

  private final EconomicMap<SlotDefinition, StorageLocation> storageLocations;
  private final EconomicMap<SlotDefinition, Class<?>>        storageTypes;

  public ObjectLayout(final EconomicSet<SlotDefinition> slots,
      final ClassFactory forClasses, final boolean isTransferObject) {
    this(getInitialStorageTypes(slots), slots.size(), forClasses,
        isTransferObject);
  }

  private static EconomicMap<SlotDefinition, Class<?>> getInitialStorageTypes(
      final EconomicSet<SlotDefinition> slots) {
    EconomicMap<SlotDefinition, Class<?>> types =
        EconomicMap.create((int) (slots.size() / 0.75f));
    for (SlotDefinition slot : slots) {
      types.put(slot, null);
    }
    return types;
  }

  public ObjectLayout(final EconomicMap<SlotDefinition, Class<?>> knownFieldTypes,
      final int numberOfFields, final ClassFactory forClasses,
      final boolean isTransferObject) {
    this.latestLayoutForClass = Truffle.getRuntime().createAssumption();
    this.forClasses = forClasses;
    this.isTransferObject = isTransferObject;

    storageTypes = knownFieldTypes;
    totalNumberOfStorageLocations = numberOfFields;
    storageLocations = EconomicMap.create((int) (numberOfFields / 0.75f));

    int nextFreePrimIdx = 0;
    int nextFreeObjIdx = 0;

    boolean onlyImmutable = true;

    MapCursor<SlotDefinition, Class<?>> entry = knownFieldTypes.getEntries();
    while (entry.advance()) {
      StorageLocation storage;
      if (entry.getValue() == Long.class) {
        storage = StorageLocation.createForLong(this, entry.getKey(), nextFreePrimIdx);
        nextFreePrimIdx++;
      } else if (entry.getValue() == Double.class) {
        storage = StorageLocation.createForDouble(this, entry.getKey(), nextFreePrimIdx);
        nextFreePrimIdx++;
      } else if (entry.getValue() == Object.class) {
        storage = StorageLocation.createForObject(this, entry.getKey(), nextFreeObjIdx);
        nextFreeObjIdx++;
      } else {
        assert entry.getValue() == null;
        storage = new UnwrittenStorageLocation(this, entry.getKey());
      }
      storageLocations.put(entry.getKey(), storage);
      onlyImmutable = onlyImmutable && entry.getKey().isImmutable();
    }

    primitiveStorageLocationsUsed = nextFreePrimIdx;
    objectStorageLocationsUsed = nextFreeObjIdx;
    onlyImmutableFields = onlyImmutable;
  }

  public boolean isValid() {
    return latestLayoutForClass.isValid();
  }

  public void checkIsLatest() throws InvalidAssumptionException {
    latestLayoutForClass.check();
  }

  public Assumption getIsLatestAssumption() {
    return latestLayoutForClass;
  }

  public boolean hasOnlyImmutableFields() {
    return onlyImmutableFields;
  }

  public boolean layoutForSameClasses(final ObjectLayout other) {
    return forClasses == other.forClasses;
  }

  public int getNumberOfFields() {
    return totalNumberOfStorageLocations;
  }

  public EconomicMap<SlotDefinition, StorageLocation> getStorageLocations() {
    return storageLocations;
  }

  public ObjectLayout withGeneralizedField(final SlotDefinition slot) {
    Class<?> type = storageTypes.get(slot);
    if (type == Object.class) {
      return this;
    } else {
      return cloneWithChanged(slot, Object.class);
    }
  }

  public ObjectLayout withInitializedField(final SlotDefinition slot, final Class<?> type) {
    Class<?> specType;
    if (type == Long.class || type == Double.class) {
      specType = type;
    } else {
      specType = Object.class;
    }

    Class<?> currentType = storageTypes.get(slot);
    if (currentType == specType) {
      return this;
    } else {
      // It can happen that two threads try to initialize the field to different types
      // This is handled here by ensuring that we generalize it when necessary.
      if ((currentType == Long.class && specType == Double.class)
          || (currentType == Double.class && specType == Long.class)) {
        specType = Object.class;
      }
      assert currentType == null;
      return cloneWithChanged(slot, specType);
    }
  }

  protected ObjectLayout cloneWithChanged(final SlotDefinition slot,
      final Class<?> specType) {
    // we create a new updated layout, and invalidate the old one
    latestLayoutForClass.invalidate();

    EconomicMap<SlotDefinition, Class<?>> withChangedField = EconomicMap.create(storageTypes);
    withChangedField.put(slot, specType);
    return new ObjectLayout(withChangedField, totalNumberOfStorageLocations,
        forClasses, isTransferObject);
  }

  public StorageLocation getStorageLocation(final SlotDefinition slot) {
    return storageLocations.get(slot);
  }

  public int getNumberOfUsedExtendedObjectStorageLocations() {
    int requiredExtensionFields = objectStorageLocationsUsed - SObject.NUM_OBJECT_FIELDS;
    if (requiredExtensionFields < 0) {
      return 0;
    }
    return requiredExtensionFields;
  }

  public int getNumberOfUsedExtendedPrimStorageLocations() {
    int requiredExtensionFields = primitiveStorageLocationsUsed - SObject.NUM_PRIMITIVE_FIELDS;
    if (requiredExtensionFields < 0) {
      return 0;
    }
    return requiredExtensionFields;
  }

  private String fieldsAndLocations() {
    String s = "";

    MapCursor<SlotDefinition, StorageLocation> e = storageLocations.getEntries();
    while (e.advance()) {
      if (!"".equals(s)) {
        s += ", ";
      }

      StorageLocation loc = e.getValue();
      String type;
      if (loc instanceof UnwrittenStorageLocation) {
        type = "unwritten";
      } else if (loc instanceof LongStorageLocation) {
        type = "long";
      } else if (loc instanceof DoubleStorageLocation) {
        type = "double";
      } else if (loc instanceof ObjectStorageLocation) {
        type = "object";
      } else {
        throw new NotYetImplementedException(); // should not be reached
      }
      s += e.getKey().getName().getString() + ":" + type;
    }
    return s;
  }

  @Override
  public String toString() {
    return "ObjLyt[" + forClasses.getClassName().getString()
        + ", " + (latestLayoutForClass.isValid() ? "valid" : "invalid") + "; "
        + fieldsAndLocations() + "]";
  }
}
