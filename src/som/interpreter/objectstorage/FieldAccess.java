package som.interpreter.objectstorage;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.TruffleCompiler;
import som.interpreter.objectstorage.StorageLocation.AbstractObjectStorageLocation;
import som.interpreter.objectstorage.StorageLocation.DoubleStorageLocation;
import som.interpreter.objectstorage.StorageLocation.LongStorageLocation;
import som.vm.constants.Nil;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;
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

  public static AbstractWriteFieldNode createWrite(final SlotDefinition slot) {
    return new UninitializedWriteFieldNode(slot);
  }

  private FieldAccess(final SlotDefinition slot) {
    super(null);
    this.slot = slot;
  }

  public abstract static class AbstractFieldRead extends FieldAccess {
    private static final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();
    public AbstractFieldRead(final SlotDefinition slot) {
      super(slot);
    }

    public abstract Object read(VirtualFrame frame, SObject obj) throws InvalidAssumptionException;

//    @Override
//    public final Object executeGeneric(final VirtualFrame frame) {
//      VM.thisMethodNeedsToBeOptimized("I think this should not be reached from compiled code. At least I think so. The cast here might be a performance issue. Otherwise, it might be fine.");
//      return read(frame, (SObject) SArguments.rcvr(frame));
//    }
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

  public static final class ReadSetLongFieldNode extends AbstractFieldRead {
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

  public static final class ReadSetOrUnsetLongFieldNode extends AbstractFieldRead {
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

  public static final class ReadSetDoubleFieldNode extends AbstractFieldRead {
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

  public static final class ReadSetOrUnsetDoubleFieldNode extends AbstractFieldRead {
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

  public abstract static class AbstractWriteFieldNode extends Node {
    protected final SlotDefinition slot;
    public AbstractWriteFieldNode(final SlotDefinition slot) {
      this.slot = slot;
    }

    public abstract Object write(SObject obj, Object value);

    public SlotDefinition getSlot() {
      return slot;
    }

    @Override
    public final String toString() {
      return getClass().getSimpleName() + "[" + slot.getName().getString() + "]";
    }

    public long write(final SObject obj, final long value) {
      write(obj, (Object) value);
      return value;
    }

    public double write(final SObject obj, final double value) {
      write(obj, (Object) value);
      return value;
    }

    protected final void writeAndRespecialize(final SObject obj, final Object value,
        final String reason, final AbstractWriteFieldNode next, final boolean locationWasSet) {
      TruffleCompiler.transferToInterpreterAndInvalidate(reason);
      obj.updateLayoutToMatchClass();

      obj.setField(slot, value);

      final ObjectLayout layout = obj.getObjectLayout();
      final StorageLocation location = layout.getStorageLocation(slot);
      AbstractWriteFieldNode newNode = location.getWriteNode(slot, layout, next, locationWasSet);
      replace(newNode, reason);
    }
  }

  public static final class UninitializedWriteFieldNode extends AbstractWriteFieldNode {
    private static final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();
    public UninitializedWriteFieldNode(final SlotDefinition slot) {
      super(slot);
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      CompilerDirectives.transferToInterpreterAndInvalidate();

      Node i = this;
      int chainDepth = 0;
      while (i.getParent() instanceof AbstractWriteFieldNode) {
        i = i.getParent();
        chainDepth++;
      }

      if (chainDepth > /* MaxChainLength */ 16) {
        // TODO: support generic read node
//        throw new RuntimeException("Megamorphic write node, this should not happen!");
      }

      writeAndRespecialize(obj, value, "initialize write field node",
          new UninitializedWriteFieldNode(slot),
          obj.getObjectLayout().getStorageLocation(slot).isSet(obj, primMarkProfile));
      return value;
    }
  }

  private abstract static class WriteSpecializedFieldNode extends AbstractWriteFieldNode {

    protected final ObjectLayout layout;
    @Child protected AbstractWriteFieldNode nextInCache;

    public WriteSpecializedFieldNode(final SlotDefinition slot,
        final ObjectLayout layout, final AbstractWriteFieldNode next) {
      super(slot);
      this.layout = layout;
      nextInCache = next;
    }

    // TODO: remove redundant guards

    protected final boolean hasExpectedLayout(final SObject obj) {
      return layout.isValid() && layout == obj.getObjectLayout();
    }
  }

  public static final class WriteSetLongFieldNode extends WriteSpecializedFieldNode {
    private final LongStorageLocation storage;
    private final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    public WriteSetLongFieldNode(final SlotDefinition slot,
        final ObjectLayout layout, final AbstractWriteFieldNode next) {
      super(slot, layout, next);
      this.storage = (LongStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public long write(final SObject obj, final long value) {
      if (hasExpectedLayout(obj) && storage.isSet(obj, primMarkProfile)) {
        storage.writeLongSet(obj, value);
      } else {
        if (layout.layoutForSameClasses(obj.getObjectLayout())) {
          CompilerDirectives.transferToInterpreter();
          writeAndRespecialize(obj, value, "update outdated write node", nextInCache, storage.isSet(obj, primMarkProfile));
        } else {
          nextInCache.write(obj, value);
        }
      }
      return value;
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      if (value instanceof Long) {
        write(obj, (long) value);
      } else {
        if (layout.layoutForSameClasses(obj.getObjectLayout())) {
          CompilerDirectives.transferToInterpreter();
          writeAndRespecialize(obj, value, "update outdated read node", nextInCache, storage.isSet(obj, primMarkProfile));
        } else {
          nextInCache.write(obj, value);
        }
      }
      return value;
    }
  }

  public static final class WriteSetOrUnsetLongFieldNode extends WriteSpecializedFieldNode {
    private final LongStorageLocation storage;
    private final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    public WriteSetOrUnsetLongFieldNode(final SlotDefinition slot,
        final ObjectLayout layout, final AbstractWriteFieldNode next) {
      super(slot, layout, next);
      this.storage = (LongStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public long write(final SObject obj, final long value) {
      if (hasExpectedLayout(obj)) {
        storage.writeLongSet(obj, value);
        storage.markAsSet(obj);
      } else {
        if (layout.layoutForSameClasses(obj.getObjectLayout())) {
          CompilerDirectives.transferToInterpreter();
          writeAndRespecialize(obj, value, "update outdated write node", nextInCache, false);
        } else {
          nextInCache.write(obj, value);
        }
      }
      return value;
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      if (value instanceof Long) {
        write(obj, (long) value);
      } else {
        if (layout.layoutForSameClasses(obj.getObjectLayout())) {
          CompilerDirectives.transferToInterpreter();
          writeAndRespecialize(obj, value, "update outdated read node", nextInCache, storage.isSet(obj, primMarkProfile));
        } else {
          nextInCache.write(obj, value);
        }
      }
      return value;
    }
  }

  public static final class WriteSetDoubleFieldNode extends WriteSpecializedFieldNode {
    private final DoubleStorageLocation storage;
    private final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    public WriteSetDoubleFieldNode(final SlotDefinition slot,
        final ObjectLayout layout, final AbstractWriteFieldNode next) {
      super(slot, layout, next);
      this.storage = (DoubleStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public double write(final SObject obj, final double value) {
      if (hasExpectedLayout(obj) && storage.isSet(obj, primMarkProfile)) {
        storage.writeDoubleSet(obj, value);
      } else {
        if (layout.layoutForSameClasses(obj.getObjectLayout())) {
          CompilerDirectives.transferToInterpreter();
          writeAndRespecialize(obj, value, "update outdated read node", nextInCache, storage.isSet(obj, primMarkProfile));
        } else {
          nextInCache.write(obj, value);
        }
      }
      return value;
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      if (value instanceof Double) {
        write(obj, (double) value);
      } else {
        if (layout.layoutForSameClasses(obj.getObjectLayout())) {
          CompilerDirectives.transferToInterpreter();
          writeAndRespecialize(obj, value, "update outdated read node", nextInCache, storage.isSet(obj, primMarkProfile));
        } else {
          nextInCache.write(obj, value);
        }
      }
      return value;
    }
  }

  public static final class WriteSetOrUnsetDoubleFieldNode extends WriteSpecializedFieldNode {
    private final DoubleStorageLocation storage;
    private final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    public WriteSetOrUnsetDoubleFieldNode(final SlotDefinition slot,
        final ObjectLayout layout, final AbstractWriteFieldNode next) {
      super(slot, layout, next);
      this.storage = (DoubleStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public double write(final SObject obj, final double value) {
      if (hasExpectedLayout(obj)) {
        storage.writeDoubleSet(obj, value);
        storage.markAsSet(obj);
      } else {
        if (layout.layoutForSameClasses(obj.getObjectLayout())) {
          CompilerDirectives.transferToInterpreter();
          writeAndRespecialize(obj, value, "update outdated read node", nextInCache, false);
        } else {
          nextInCache.write(obj, value);
        }
      }
      return value;
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      if (value instanceof Double) {
        write(obj, (double) value);
      } else {
        if (layout.layoutForSameClasses(obj.getObjectLayout())) {
          CompilerDirectives.transferToInterpreter();
          writeAndRespecialize(obj, value, "update outdated read node", nextInCache, storage.isSet(obj, primMarkProfile));
        } else {
          nextInCache.write(obj, value);
        }
      }
      return value;
    }
  }

  public static final class WriteObjectFieldNode extends WriteSpecializedFieldNode {
    private final AbstractObjectStorageLocation storage;

    public WriteObjectFieldNode(final SlotDefinition slot,
        final ObjectLayout layout, final AbstractWriteFieldNode next) {
      super(slot, layout, next);
      this.storage = (AbstractObjectStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      if (hasExpectedLayout(obj)) {
        storage.write(obj, value);
      } else {
        if (layout.layoutForSameClasses(obj.getObjectLayout())) {
          CompilerDirectives.transferToInterpreter();
          writeAndRespecialize(obj, value, "update outdated read node", nextInCache, false);
        } else {
          nextInCache.write(obj, value);
        }
      }
      return value;
    }
  }
}
