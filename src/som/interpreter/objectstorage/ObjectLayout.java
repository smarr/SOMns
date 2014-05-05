package som.interpreter.objectstorage;

import som.interpreter.objectstorage.StorageLocation.UnwrittenStorageLocation;
import som.vmobjects.SObject;



public final class ObjectLayout {
  private final int primitiveStorageLocationsUsed;
  private final int objectStorageLocationsUsed;
  private final int totalNumberOfStorageLocations;

  private final StorageLocation[] storageLocations;
  private final Class<?>[]        storageTypes;

  public ObjectLayout(final int numberOfFields) {
    this(new Class<?>[numberOfFields]);
  }

  public ObjectLayout(final Class<?>[] knownFieldTypes) {
    storageTypes = knownFieldTypes;
    totalNumberOfStorageLocations = knownFieldTypes.length;
    storageLocations = new StorageLocation[knownFieldTypes.length];

    int nextFreePrimIdx = 0;
    int nextFreeObjIdx  = 0;

    for (int i = 0; i < totalNumberOfStorageLocations; i++) {
      Class<?> type = knownFieldTypes[i];

      StorageLocation storage;
      if (type == Long.class) {
        storage = StorageLocation.createForLong(this, nextFreePrimIdx);
        nextFreePrimIdx++;
      } else if (type == Double.class) {
        storage = StorageLocation.createForDouble(this, nextFreePrimIdx);
        nextFreePrimIdx++;
      } else if (type == Object.class) {
        storage = StorageLocation.createForObject(this, nextFreeObjIdx);
        nextFreeObjIdx++;
      } else {
        assert type == null;
        storage = new UnwrittenStorageLocation(this);
      }

      storageLocations[i] = storage;
    }

    primitiveStorageLocationsUsed = nextFreePrimIdx;
    objectStorageLocationsUsed    = nextFreeObjIdx;
  }

  public int getNumberOfFields() {
    return storageTypes.length;
  }

  public ObjectLayout withGeneralizedField(final long fieldIndex) {
    return withGeneralizedField((int) fieldIndex);
  }

  public ObjectLayout withGeneralizedField(final int fieldIndex) {
    Class<?>[] withGeneralizedField = storageTypes.clone();
    withGeneralizedField[fieldIndex] = Object.class;
    return new ObjectLayout(withGeneralizedField);
  }

  public ObjectLayout withInitializedField(final long fieldIndex, final Class<?> type) {
    Class <?> specType;
    if (type == Long.class || type == Double.class) {
      specType = type;
    } else {
      specType = Object.class;
    }
    return withInitializedField((int) fieldIndex, specType);
  }

  private ObjectLayout withInitializedField(final int fieldIndex, final Class<?> type) {
    assert storageTypes[fieldIndex] == null;
    Class<?>[] withInitializedField = storageTypes.clone();
    withInitializedField[fieldIndex] = type;
    return new ObjectLayout(withInitializedField);
  }

  public StorageLocation getStorageLocation(final long fieldIndex) {
    return getStorageLocation((int) fieldIndex);
  }

  public StorageLocation getStorageLocation(final int fieldIndex) {
    return storageLocations[fieldIndex];
  }

  public int getNumberOfUsedExtendedObjectStorageLocations() {
    int requiredExtensionFields = objectStorageLocationsUsed - SObject.NUM_OBJECT_FIELDS;
    if (requiredExtensionFields < 0) { requiredExtensionFields = 0; }
    return requiredExtensionFields;
  }

  public int getNumberOfUsedExtendedPrimStorageLocations() {
    int requiredExtensionFields = primitiveStorageLocationsUsed - SObject.NUM_PRIMITIVE_FIELDS;
    if (requiredExtensionFields < 0) { requiredExtensionFields = 0;  }
    return requiredExtensionFields;
  }
}
