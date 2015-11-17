package som.interpreter.actors;

import java.util.HashMap;
import java.util.Map;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.objectstorage.ObjectLayout;
import som.interpreter.objectstorage.StorageLocation;
import som.interpreter.objectstorage.StorageLocation.GeneralizeStorageLocationException;
import som.interpreter.objectstorage.StorageLocation.UninitalizedStorageLocationException;
import som.vm.constants.Classes;
import som.vmobjects.SObject;


public final class TransferObject {

  public static SObject transfer(final SObject obj, final Actor origin,
      final Actor target, final Map<SObject, SObject> transferedObjects) {
    assert obj.getSOMClass().isKindOf(Classes.transferClass) : "only TransferObjects should be handled here";
    assert !obj.isValue() : "TransferObjects can't be Values";

    ObjectLayout layout = obj.getObjectLayout();
    HashMap<SlotDefinition, StorageLocation> fields = layout.getStorageLocations();
    SObject newObj = obj.cloneBasics();

    Map<SObject, SObject> transferMap;
    if (transferedObjects != null) {
      transferMap = transferedObjects;
    } else {
      transferMap = new HashMap<SObject, SObject>();
    }

    assert !transferMap.containsKey(obj) : "The algorithm should not transfer an object twice.";
    transferMap.put(obj, newObj);

    for (StorageLocation location : fields.values()) {
      if (location.isObjectLocation()) {
        Object orgObj = location.read(obj);

        // if it was already transfered, take it from the map, otherwise, handle it
        Object trnfObj = transferMap.get(orgObj);
        if (trnfObj == null) {
          trnfObj = target.wrapForUse(orgObj, origin, transferMap);
        }
        try {
          location.write(newObj, trnfObj);
        } catch (GeneralizeStorageLocationException
            | UninitalizedStorageLocationException e) {
          assert false : "this should never be reached, because we only write initialized slots";
        }
      }
    }
    return newObj;
  }
}
