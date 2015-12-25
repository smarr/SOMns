package som.interpreter.objectstorage;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.TruffleCompiler;
import som.interpreter.TypesGen;
import som.interpreter.objectstorage.StorageLocation.AbstractObjectStorageLocation;
import som.interpreter.objectstorage.StorageLocation.DoubleStorageLocation;
import som.interpreter.objectstorage.StorageLocation.LongStorageLocation;
import som.vm.constants.Nil;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.IntValueProfile;


public abstract class FieldAccessorNode extends Node {
  protected final SlotDefinition slot;

  public static AbstractReadFieldNode createRead(final SlotDefinition slot) {
    return new UninitializedReadFieldNode(slot);
  }

  public static AbstractWriteFieldNode createWrite(final SlotDefinition slot) {
    return new UninitializedWriteFieldNode(slot);
  }

  private FieldAccessorNode(final SlotDefinition slot) {
    this.slot = slot;
  }

  public final SlotDefinition getSlot() {
    return slot;
  }

  public abstract static class AbstractReadFieldNode extends FieldAccessorNode {
    private static final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();
    public AbstractReadFieldNode(final SlotDefinition slot) {
      super(slot);
    }

    public abstract Object read(SObject obj);

    public long readLong(final SObject obj) throws UnexpectedResultException {
      return TypesGen.expectLong(read(obj));
    }

    public double readDouble(final SObject obj) throws UnexpectedResultException {
      return TypesGen.expectDouble(read(obj));
    }

    protected final Object specializeAndRead(final SObject obj, final String reason, final AbstractReadFieldNode next) {
      TruffleCompiler.transferToInterpreterAndInvalidate(reason);
      return specialize(obj, reason, next).read(obj);
    }

    protected final AbstractReadFieldNode specialize(final SObject obj,
        final String reason, final AbstractReadFieldNode next) {
      TruffleCompiler.transferToInterpreterAndInvalidate(reason);
      obj.updateLayoutToMatchClass();

      final ObjectLayout    layout   = obj.getObjectLayout();
      final StorageLocation location = layout.getStorageLocation(slot);

      AbstractReadFieldNode newNode = location.getReadNode(slot, layout, next, location.isSet(obj, primMarkProfile));
      return replace(newNode, reason);
    }
  }

  public static final class UninitializedReadFieldNode extends AbstractReadFieldNode {

    public UninitializedReadFieldNode(final SlotDefinition slot) {
      super(slot);
    }

    @Override
    public Object read(final SObject obj) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return specializeAndRead(obj, "uninitalized node", new UninitializedReadFieldNode(slot));
    }
  }

  public abstract static class ReadSpecializedFieldNode extends AbstractReadFieldNode {
    protected final ObjectLayout layout;
    @Child private AbstractReadFieldNode nextInCache;

    public ReadSpecializedFieldNode(final SlotDefinition slot,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      super(slot);
      this.layout = layout;
      nextInCache = next;
    }

    protected final boolean hasExpectedLayout(final SObject obj) {
      return layout == obj.getObjectLayout();
    }

    protected final AbstractReadFieldNode respecializedNodeOrNext(final SObject obj) {
      if (layout.layoutForSameClasses(obj.getObjectLayout())) {
        CompilerDirectives.transferToInterpreter();
        return specialize(obj, "update outdated read node", nextInCache);
      } else {
        return nextInCache;
      }
    }
  }

  public static final class ReadUnwrittenFieldNode extends ReadSpecializedFieldNode {
    public ReadUnwrittenFieldNode(final SlotDefinition slot,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      super(slot, layout, next);
    }

    @Override
    public Object read(final SObject obj) {
      if (hasExpectedLayout(obj)) {
        return Nil.nilObject;
      } else {
        return respecializedNodeOrNext(obj).read(obj);
      }
    }
  }

  public static final class ReadSetLongFieldNode extends ReadSpecializedFieldNode {
    private final LongStorageLocation storage;
    private final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    public ReadSetLongFieldNode(final SlotDefinition slot,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      super(slot, layout, next);
      this.storage = (LongStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public long readLong(final SObject obj) throws UnexpectedResultException {
      if (hasExpectedLayout(obj) && storage.isSet(obj, primMarkProfile)) {
        return storage.readLongSet(obj);
      } else {
        return respecializedNodeOrNext(obj).
            readLong(obj);
      }
    }

    @Override
    public Object read(final SObject obj) {
      try {
        return readLong(obj);
      } catch (UnexpectedResultException e) {
        return e.getResult();
      }
    }
  }

  public static final class ReadSetOrUnsetLongFieldNode extends ReadSpecializedFieldNode {
    private final LongStorageLocation storage;
    private final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    public ReadSetOrUnsetLongFieldNode(final SlotDefinition slot,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      super(slot, layout, next);
      this.storage = (LongStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public long readLong(final SObject obj) throws UnexpectedResultException {
      if (hasExpectedLayout(obj)) {
        if (storage.isSet(obj, primMarkProfile)) {
          return storage.readLongSet(obj);
        } else {
          CompilerDirectives.transferToInterpreter();
          throw new UnexpectedResultException(Nil.nilObject);
        }
      } else {
        return respecializedNodeOrNext(obj).
            readLong(obj);
      }
    }

    @Override
    public Object read(final SObject obj) {
      try {
        return readLong(obj);
      } catch (UnexpectedResultException e) {
        return e.getResult();
      }
    }
  }

  public static final class ReadSetDoubleFieldNode extends ReadSpecializedFieldNode {
    private final DoubleStorageLocation storage;
    private final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    public ReadSetDoubleFieldNode(final SlotDefinition slot,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      super(slot, layout, next);
      this.storage = (DoubleStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public double readDouble(final SObject obj) throws UnexpectedResultException {
      if (hasExpectedLayout(obj) && storage.isSet(obj, primMarkProfile)) {
        return storage.readDoubleSet(obj);
      } else {
        return respecializedNodeOrNext(obj).readDouble(obj);
      }
    }

    @Override
    public Object read(final SObject obj) {
      try {
        return readDouble(obj);
      } catch (UnexpectedResultException e) {
        return e.getResult();
      }
    }
  }

  public static final class ReadSetOrUnsetDoubleFieldNode extends ReadSpecializedFieldNode {
    private final DoubleStorageLocation storage;
    private final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    public ReadSetOrUnsetDoubleFieldNode(final SlotDefinition slot,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      super(slot, layout, next);
      this.storage = (DoubleStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public double readDouble(final SObject obj) throws UnexpectedResultException {
      if (hasExpectedLayout(obj)) {
        if (storage.isSet(obj, primMarkProfile)) {
          return storage.readDoubleSet(obj);
        } else {
          CompilerDirectives.transferToInterpreter();
          throw new UnexpectedResultException(Nil.nilObject);
        }
      } else {
        return respecializedNodeOrNext(obj).readDouble(obj);
      }
    }

    @Override
    public Object read(final SObject obj) {
      try {
        return readDouble(obj);
      } catch (UnexpectedResultException e) {
        return e.getResult();
      }
    }
  }

  public static final class ReadObjectFieldNode extends ReadSpecializedFieldNode {
    private final AbstractObjectStorageLocation storage;

    public ReadObjectFieldNode(final SlotDefinition slot,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      super(slot, layout, next);
      this.storage = (AbstractObjectStorageLocation) layout.getStorageLocation(slot);
    }

    @Override
    public Object read(final SObject obj) {
      if (hasExpectedLayout(obj)) {
        return storage.read(obj);
      } else {
        return respecializedNodeOrNext(obj).read(obj);
      }
    }
  }

  public abstract static class AbstractWriteFieldNode extends FieldAccessorNode {
    public AbstractWriteFieldNode(final SlotDefinition slot) {
      super(slot);
    }

    public abstract Object write(SObject obj, Object value);

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

    protected final boolean hasExpectedLayout(final SObject obj) {
      return layout == obj.getObjectLayout();
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
