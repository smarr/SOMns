package som.interpreter.actors;

import java.util.HashMap;
import java.util.Map;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.objectstorage.ObjectLayout;
import som.interpreter.objectstorage.StorageLocation;
import som.vm.NotYetImplementedException;
import som.vm.constants.Nil;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SArray.PartiallyEmptyArray;
import som.vmobjects.SArray.STransferArray;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;


public final class TransferObject {

  public static boolean isTransferObject(final Object obj) {
    if (obj instanceof STransferArray) {
      return true;
    }
    if (obj instanceof SObjectWithClass) {
      return ((SObjectWithClass) obj).getSOMClass().isTransferObject();
    } else {
      return false;
    }
  }

  @TruffleBoundary
  public static SObjectWithoutFields transfer(final SObjectWithoutFields obj,
      final Actor orgin, final Actor target,
      final Map<SAbstractObject, SAbstractObject> transferedObjects) {
    SObjectWithoutFields newObj = obj.cloneBasics();
    if (transferedObjects != null) {
      transferedObjects.put(obj, newObj);
    }
    return newObj;
  }

  @TruffleBoundary
  public static SObject transfer(final SObject obj, final Actor origin,
      final Actor target,
      final Map<SAbstractObject, SAbstractObject> transferedObjects) {
    assert obj.getSOMClass()
              .isTransferObject() : "only TransferObjects should be handled here";
    assert !obj.isValue() : "TransferObjects can't be Values";

    ObjectLayout layout = obj.getObjectLayout();
    HashMap<SlotDefinition, StorageLocation> fields = layout.getStorageLocations();
    SObject newObj = obj.cloneBasics();

    Map<SAbstractObject, SAbstractObject> transferMap =
        takeOrCreateTransferMap(transferedObjects);

    assert !transferMap.containsKey(
        obj) : "The algorithm should not transfer an object twice.";
    transferMap.put(obj, newObj);

    for (StorageLocation location : fields.values()) {
      if (location.isObjectLocation()) {
        Object orgObj = location.read(obj);

        // if it was already transfered, take it from the map, otherwise, handle it
        Object trnfObj = transferMap.get(orgObj);
        if (trnfObj == null) {
          trnfObj = target.wrapForUse(orgObj, origin, transferMap);
        }
        location.write(newObj, trnfObj);
      }
    }
    return newObj;
  }

  @TruffleBoundary
  public static STransferArray transfer(final STransferArray arr,
      final Actor origin, final Actor target,
      final Map<SAbstractObject, SAbstractObject> transferedObjects) {
    STransferArray newObj = arr.cloneBasics();

    if (newObj.isSomePrimitiveType() || newObj.isEmptyType()) {
      return newObj; // we are done in this case
    }

    assert newObj.isPartiallyEmptyType() || newObj.isObjectType();

    Map<SAbstractObject, SAbstractObject> transferMap =
        takeOrCreateTransferMap(transferedObjects);

    assert !transferMap.containsKey(
        arr) : "The algorithm should not transfer an object twice.";
    transferMap.put(arr, newObj);

    if (newObj.isObjectType()) {
      Object[] storage = newObj.getObjectStorage(SArray.ObjectStorageType);

      for (int i = 0; i < storage.length; i++) {
        Object orgObj = storage[i];

        // if it was already transfered, take it from the map, otherwise, handle it
        Object trnfObj = transferMap.get(orgObj);
        if (trnfObj == null) {
          trnfObj = target.wrapForUse(orgObj, origin, transferMap);
        }

        storage[i] = trnfObj;
      }
    } else if (newObj.isPartiallyEmptyType()) {
      PartiallyEmptyArray parr =
          newObj.getPartiallyEmptyStorage(SArray.PartiallyEmptyStorageType);
      Object[] storage = parr.getStorage();

      for (int i = 0; i < storage.length; i++) {
        Object orgObj = storage[i];

        if (orgObj == Nil.nilObject) {
          continue;
        }

        // if it was already transfered, take it from the map, otherwise, handle it
        Object trnfObj = transferMap.get(orgObj);
        if (trnfObj == null) {
          trnfObj = target.wrapForUse(orgObj, origin, transferMap);
        }

        storage[i] = trnfObj;
      }
    } else {
      CompilerDirectives.transferToInterpreter();
      assert false : "Missing support for some storage type";
      throw new NotYetImplementedException();
    }

    return newObj;
  }

  protected static Map<SAbstractObject, SAbstractObject> takeOrCreateTransferMap(
      final Map<SAbstractObject, SAbstractObject> transferedObjects) {
    Map<SAbstractObject, SAbstractObject> transferMap;
    if (transferedObjects != null) {
      transferMap = transferedObjects;
    } else {
      transferMap = new HashMap<SAbstractObject, SAbstractObject>();
    }
    return transferMap;
  }
}
