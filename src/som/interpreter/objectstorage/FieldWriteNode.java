package som.interpreter.objectstorage;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.objectstorage.StorageLocation.AbstractObjectStorageLocation;
import som.interpreter.objectstorage.StorageLocation.DoubleStorageLocation;
import som.interpreter.objectstorage.StorageLocation.LongStorageLocation;
import som.interpreter.objectstorage.StorageLocation.UnwrittenStorageLocation;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.IntValueProfile;


public final class FieldWriteNode  {
  private static final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

  public static AbstractFieldWriteNode createWrite(final SlotDefinition slot,
      final SObject obj, final Object value) {
    StorageLocation location = obj.getObjectLayout().getStorageLocation(slot);
    return createWrite(slot, location, obj, value);
  }

  public static AbstractFieldWriteNode createWrite(final SlotDefinition slot,
      final StorageLocation location, final SObject obj, final Object value) {
    if (value instanceof Long) {
      if (location instanceof LongStorageLocation) {
        LongStorageLocation l = (LongStorageLocation) location;
        if (l.isSet(obj, primMarkProfile)) {
          return new WriteSetLong(slot, l);
        } else {
          return new WriteSetOrUnsetLong(slot, l);
        }
      }
    } else if (value instanceof Double) {
      if (location instanceof DoubleStorageLocation) {
        DoubleStorageLocation l = (DoubleStorageLocation) location;
        if (l.isSet(obj, primMarkProfile)) {
          return new WriteSetDouble(slot, l);
        } else {
          return new WriteSetOrUnsetDouble(slot, l);
        }
      }
    }

    if (location instanceof UnwrittenStorageLocation) {
      return new WriteUnwritten(slot);
    } else {
      return new WriteObject(slot, (AbstractObjectStorageLocation) location);
    }
  }

  public static AbstractFieldWriteNode createWriteObject(final SlotDefinition slot, final SObject obj) {
    StorageLocation loc = obj.getObjectLayout().getStorageLocation(slot);
    if (loc instanceof AbstractObjectStorageLocation) {
      return new WriteObject(slot, (AbstractObjectStorageLocation) loc);
    } else {
      assert loc instanceof UnwrittenStorageLocation;
      return new WriteUnwritten(slot);
    }
  }

  public abstract static class AbstractFieldWriteNode extends Node {

    protected final SlotDefinition slot;

    AbstractFieldWriteNode(final SlotDefinition slot) {
      this.slot  = slot;
    }

    public abstract Object write(SObject obj, Object value);
  }

  public abstract static class AbstractWriteLong extends AbstractFieldWriteNode {
    protected final LongStorageLocation location;

    public AbstractWriteLong(final SlotDefinition slot, final LongStorageLocation location) {
      super(slot);
      this.location = location;
    }

    public abstract long writeLong(SObject obj, long value);
  }

  private static final class WriteSetLong extends AbstractWriteLong {

    protected final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    WriteSetLong(final SlotDefinition slot, final LongStorageLocation location) {
      super(slot, location);
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      if (value instanceof Long) {
        return writeAndEnsureWasSetBefore(obj, (long) value);
      } else {
        CompilerDirectives.transferToInterpreter();
        // write the value using the fall back, this is slow, I hope this
        // is never going to be on the fast path, because the object layout
        // is changed and this node should be removed from the dispatch chain
        obj.writeSlot(slot, value);
        return value;
      }
    }

    @Override
    public long writeLong(final SObject obj, final long value) {
      return writeAndEnsureWasSetBefore(obj, value);
    }

    private long writeAndEnsureWasSetBefore(final SObject selfArg, final long valueArg) {
      location.writeLongSet(selfArg, valueArg);
      if (!location.isSet(selfArg, primMarkProfile)) {
        CompilerDirectives.transferToInterpreter();

        // fall back to WriteSetOrUnsetLong, but first set this
        location.markAsSet(selfArg);
        replace(new WriteSetOrUnsetLong(slot, location));
      }
      return valueArg;
    }
  }

  private static final class WriteSetOrUnsetLong extends AbstractWriteLong {

    WriteSetOrUnsetLong(final SlotDefinition slot, final LongStorageLocation location) {
      super(slot, location);
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      if (value instanceof Long) {
        return writeLong(obj, (long) value);
      } else {
        CompilerDirectives.transferToInterpreter();
        // write the value using the fall back, this is slow, I hope this
        // is never going to be on the fast path, because the object layout
        // is changed and this node should be removed from the dispatch chain
        obj.writeSlot(slot, value);
        return value;
      }
    }

    @Override
    public long writeLong(final SObject obj, final long value) {
      location.writeLongSet(obj, value);
      location.markAsSet(obj);
      return value;
    }
  }

  private static final class WriteSetDouble extends AbstractFieldWriteNode {
    private final DoubleStorageLocation location;
    private final IntValueProfile primMarkProfile = IntValueProfile.createIdentityProfile();

    WriteSetDouble(final SlotDefinition slot, final DoubleStorageLocation location) {
      super(slot);
      this.location = location;
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      if (value instanceof Double) {
        writeAndEnsureWasSetBefore(obj, (double) value);
      } else {
        CompilerDirectives.transferToInterpreter();
        // write the value using the fall back, this is slow, I hope this
        // is never going to be on the fast path, because the object layout
        // is changed and this node should be removed from the dispatch chain
        obj.writeSlot(slot, value);
      }
      return value;
    }

    private void writeAndEnsureWasSetBefore(final SObject selfArg, final double valueArg) {
      location.writeDoubleSet(selfArg, valueArg);
      if (!location.isSet(selfArg, primMarkProfile)) {
        CompilerDirectives.transferToInterpreter();

        // fall back to WriteSetOrUnsetLong, but first set this
        location.markAsSet(selfArg);
        replace(new WriteSetOrUnsetDouble(slot, location));
      }
    }
  }

  private static final class WriteSetOrUnsetDouble extends AbstractFieldWriteNode {
    private final DoubleStorageLocation location;

    WriteSetOrUnsetDouble(final SlotDefinition slot, final DoubleStorageLocation location) {
      super(slot);
      this.location = location;
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      if (value instanceof Double) {
        location.writeDoubleSet(obj, (double) value);
        location.markAsSet(obj);
      } else {
        CompilerDirectives.transferToInterpreter();
        // write the value using the fall back, this is slow, I hope this
        // is never going to be on the fast path, because the object layout
        // is changed and this node should be removed from the dispatch chain
        obj.writeSlot(slot, value);
      }
      return value;
    }
  }

  private static final class WriteObject extends AbstractFieldWriteNode {
    private final AbstractObjectStorageLocation location;

    WriteObject(final SlotDefinition slot,
        final AbstractObjectStorageLocation location) {
      super(slot);
      this.location = location;
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      location.write(obj, value);
      return value;
    }
  }

  private static final class WriteUnwritten extends AbstractFieldWriteNode {
    WriteUnwritten(final SlotDefinition slot) {
      super(slot);
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      CompilerAsserts.neverPartOfCompilation("Should never be part of a compiled AST.");
      obj.writeSlot(slot, value);
      return value;
    }
  }
}
