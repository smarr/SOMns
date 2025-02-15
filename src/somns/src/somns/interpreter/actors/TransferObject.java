package somns.interpreter.actors;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import somns.compiler.MixinDefinition.SlotDefinition;
import somns.interpreter.objectstorage.ObjectLayout;
import somns.interpreter.objectstorage.StorageLocation;
import somns.vm.NotYetImplementedException;
import somns.vm.constants.Nil;
import somns.vmobjects.SAbstractObject;
import somns.vmobjects.SObject;
import somns.vmobjects.SObjectWithClass;
import somns.vmobjects.SArray.PartiallyEmptyArray;
import somns.vmobjects.SArray.STransferArray;
import somns.vmobjects.SObjectWithClass.SObjectWithoutFields;


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
              .isTransferObject()
        : "only TransferObjects should be handled here";
    assert !obj.isValue() : "TransferObjects can't be Values";

    ObjectLayout layout = obj.getObjectLayout();
    EconomicMap<SlotDefinition, StorageLocation> fields = layout.getStorageLocations();
    SObject newObj = obj.cloneBasics();

    Map<SAbstractObject, SAbstractObject> transferMap =
        takeOrCreateTransferMap(transferedObjects);

    assert !transferMap.containsKey(
        obj) : "The algorithm should not transfer an object twice.";
    transferMap.put(obj, newObj);

    for (StorageLocation location : fields.getValues()) {
      if (location.isObjectLocation()) {
        Object orgObj = location.read(obj);

        // if it was already transfered, take it from the map, otherwise, handle it
        Object trnfObj = transferMap.get(orgObj);
        if (trnfObj == null) {
          trnfObj = WrapReferenceNode.wrapForUse(target, orgObj, origin, transferMap);
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
      Object[] storage = newObj.getObjectStorage();

      for (int i = 0; i < storage.length; i++) {
        Object orgObj = storage[i];

        // if it was already transfered, take it from the map, otherwise, handle it
        Object trnfObj = transferMap.get(orgObj);
        if (trnfObj == null) {
          trnfObj = WrapReferenceNode.wrapForUse(target, orgObj, origin, transferMap);
        }

        storage[i] = trnfObj;
      }
    } else if (newObj.isPartiallyEmptyType()) {
      PartiallyEmptyArray parr =
          newObj.getPartiallyEmptyStorage();
      Object[] storage = parr.getStorage();

      for (int i = 0; i < storage.length; i++) {
        Object orgObj = storage[i];

        if (orgObj == Nil.nilObject) {
          continue;
        }

        // if it was already transfered, take it from the map, otherwise, handle it
        Object trnfObj = transferMap.get(orgObj);
        if (trnfObj == null) {
          trnfObj = WrapReferenceNode.wrapForUse(target, orgObj, origin, transferMap);
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
