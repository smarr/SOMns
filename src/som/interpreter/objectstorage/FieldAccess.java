package som.interpreter.objectstorage;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.objectstorage.StorageLocation.AbstractObjectStorageLocation;
import som.interpreter.objectstorage.StorageLocation.PrimitiveStorageLocation;
import som.vm.constants.Nil;
import som.vmobjects.SObject;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.IntValueProfile;


public abstract class FieldAccess extends Node {
  private static final IntValueProfile staticPrimMarkProfile = IntValueProfile.createIdentityProfile();

  protected final SlotDefinition slot;

  public final SlotDefinition getSlot() {
    return slot;
  }

  public static AbstractFieldRead createRead(final SlotDefinition slot, final SObject rcvr) {
    ObjectLayout layout = rcvr.getObjectLayout();
    final StorageLocation location = layout.getStorageLocation(slot);
    return location.getReadNode(slot, layout, location.isSet(rcvr, staticPrimMarkProfile));
  }

  private FieldAccess(final SlotDefinition slot) {
    super(null);
    this.slot = slot;
  }

  public abstract static class AbstractFieldRead extends FieldAccess {
    public AbstractFieldRead(final SlotDefinition slot) {
      super(slot);
    }

    public abstract Object read(VirtualFrame frame, SObject obj) throws InvalidAssumptionException;
  }

  public static final class ReadUnwrittenFieldNode extends AbstractFieldRead {
    public ReadUnwrittenFieldNode(final SlotDefinition slot) {
      super(slot);
    }

    @Override
    public Object read(final VirtualFrame frame, final SObject obj) {
      assert obj.getObjectLayout().isValid();
      return Nil.nilObject;
    }
  }

  public static final class ReadSetPrimitiveSlot extends AbstractFieldRead {
    private final PrimitiveStorageLocation storage;
    private final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    public ReadSetPrimitiveSlot(final SlotDefinition slot,
        final ObjectLayout layout) {
      super(slot);
      this.storage = (PrimitiveStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public Object read(final VirtualFrame frame, final SObject obj) throws InvalidAssumptionException {
      if (storage.isSet(obj, primMarkProfile)) {
        assert obj.getObjectLayout().isValid();
        return storage.readSet(obj);
      } else {
        throw new InvalidAssumptionException();
      }
    }
  }

  public static final class ReadSetOrUnsetPrimitiveSlot extends AbstractFieldRead {
    private final PrimitiveStorageLocation storage;
    private final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    public ReadSetOrUnsetPrimitiveSlot(final SlotDefinition slot,
        final ObjectLayout layout) {
      super(slot);
      this.storage = (PrimitiveStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public Object read(final VirtualFrame frame, final SObject obj) {
      if (storage.isSet(obj, primMarkProfile)) {
        assert obj.getObjectLayout().isValid();
        return storage.readSet(obj);
      } else {
        return Nil.nilObject;
      }
    }
  }

  public static final class ReadObjectFieldNode extends AbstractFieldRead {
    private final AbstractObjectStorageLocation storage;

    public ReadObjectFieldNode(final SlotDefinition slot,
        final ObjectLayout layout) {
      super(slot);
      this.storage = (AbstractObjectStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public Object read(final VirtualFrame frame, final SObject obj) {
      return storage.read(obj);
    }
  }
}
