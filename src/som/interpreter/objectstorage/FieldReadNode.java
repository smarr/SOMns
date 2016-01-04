package som.interpreter.objectstorage;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.objectstorage.StorageLocation.AbstractObjectStorageLocation;
import som.interpreter.objectstorage.StorageLocation.DoubleStorageLocation;
import som.interpreter.objectstorage.StorageLocation.LongStorageLocation;
import som.vm.constants.Nil;
import som.vmobjects.SObject;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.IntValueProfile;


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
    super(null);
    this.slot = slot;
  }

  public abstract Object read(VirtualFrame frame, SObject obj) throws InvalidAssumptionException;

  public static final class ReadUnwrittenFieldNode extends FieldReadNode {
    public ReadUnwrittenFieldNode(final SlotDefinition slot) {
      super(slot);
    }

    @Override
    public Object read(final VirtualFrame frame, final SObject obj) {
      assert obj.getObjectLayout().isValid();
      return Nil.nilObject;
    }
  }

  public static final class ReadSetLongFieldNode extends FieldReadNode {
    private final LongStorageLocation storage;
    private final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    public ReadSetLongFieldNode(final SlotDefinition slot,
        final ObjectLayout layout) {
      super(slot);
      this.storage = (LongStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public Object read(final VirtualFrame frame, final SObject obj) throws InvalidAssumptionException {
      if (storage.isSet(obj, primMarkProfile)) {
        assert obj.getObjectLayout().isValid();
        return storage.readLongSet(obj);
      } else {
        throw new InvalidAssumptionException();
      }
    }
  }

  public static final class ReadSetOrUnsetLongFieldNode extends FieldReadNode {
    private final LongStorageLocation storage;
    private final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    public ReadSetOrUnsetLongFieldNode(final SlotDefinition slot,
        final ObjectLayout layout) {
      super(slot);
      this.storage = (LongStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public Object read(final VirtualFrame frame, final SObject obj) {
      if (storage.isSet(obj, primMarkProfile)) {
        assert obj.getObjectLayout().isValid();
        return storage.readLongSet(obj);
      } else {
        return Nil.nilObject;
      }
    }
  }

  public static final class ReadSetDoubleFieldNode extends FieldReadNode {
    private final DoubleStorageLocation storage;
    private final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    public ReadSetDoubleFieldNode(final SlotDefinition slot,
        final ObjectLayout layout) {
      super(slot);
      this.storage = (DoubleStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public Object read(final VirtualFrame frame, final SObject obj)
        throws InvalidAssumptionException {
      if (storage.isSet(obj, primMarkProfile)) {
        assert obj.getObjectLayout().isValid();
        return storage.readDoubleSet(obj);
      } else {
        throw new InvalidAssumptionException();
      }
    }
  }

  public static final class ReadSetOrUnsetDoubleFieldNode extends FieldReadNode {
    private final DoubleStorageLocation storage;
    private final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    public ReadSetOrUnsetDoubleFieldNode(final SlotDefinition slot,
        final ObjectLayout layout) {
      super(slot);
      this.storage = (DoubleStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public Object read(final VirtualFrame frame, final SObject obj) {
      if (storage.isSet(obj, primMarkProfile)) {
        assert obj.getObjectLayout().isValid();
        return storage.readDoubleSet(obj);
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
    public Object read(final VirtualFrame frame, final SObject obj) {
      return storage.read(obj);
    }
  }
}
