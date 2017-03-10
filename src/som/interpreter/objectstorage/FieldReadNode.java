package som.interpreter.objectstorage;

import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.IntValueProfile;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.objectstorage.StorageLocation.AbstractObjectStorageLocation;
import som.interpreter.objectstorage.StorageLocation.PrimitiveStorageLocation;
import som.vm.constants.Nil;
import som.vmobjects.SObject;


public abstract class FieldReadNode extends Node {
  private static final IntValueProfile staticPrimMarkProfile = IntValueProfile.createIdentityProfile();

  protected final SlotDefinition slot;

  public final SlotDefinition getSlot() {
    return slot;
  }

  public static FieldReadNode createRead(final SlotDefinition slot, final SObject rcvr) {
    ObjectLayout layout = rcvr.getObjectLayout();
    final StorageLocation location = layout.getStorageLocation(slot);
    return location.getReadNode(location.isSet(rcvr, staticPrimMarkProfile));
  }

  protected FieldReadNode(final SlotDefinition slot) {
    super();
    this.slot = slot;
  }

    public abstract Object read(SObject obj) throws InvalidAssumptionException;

  public static final class ReadUnwrittenFieldNode extends FieldReadNode {
    public ReadUnwrittenFieldNode(final SlotDefinition slot) {
      super(slot);
    }

    @Override
    public Object read(final SObject obj) {
      return Nil.nilObject;
    }
  }

  public static final class ReadSetPrimitiveSlot extends FieldReadNode {
    private final PrimitiveStorageLocation storage;
    private final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    public ReadSetPrimitiveSlot(final SlotDefinition slot,
        final ObjectLayout layout) {
      super(slot);
      this.storage = (PrimitiveStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public Object read(final SObject obj) throws InvalidAssumptionException {
      if (storage.isSet(obj, primMarkProfile)) {
        return storage.readSet(obj);
      } else {
        throw new InvalidAssumptionException();
      }
    }
  }

  public static final class ReadSetOrUnsetPrimitiveSlot extends FieldReadNode {
    private final PrimitiveStorageLocation storage;
    private final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    public ReadSetOrUnsetPrimitiveSlot(final SlotDefinition slot,
        final ObjectLayout layout) {
      super(slot);
      this.storage = (PrimitiveStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public Object read(final SObject obj) {
      if (storage.isSet(obj, primMarkProfile)) {
        return storage.readSet(obj);
      } else {
        return Nil.nilObject;
      }
    }
  }

  public static final class ReadObjectFieldNode extends FieldReadNode {
    private final AbstractObjectStorageLocation storage;

    public ReadObjectFieldNode(final SlotDefinition slot,
        final ObjectLayout layout) {
      super(slot);
      this.storage = (AbstractObjectStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public Object read(final SObject obj) {
      return storage.read(obj);
    }
  }
}
