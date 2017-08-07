package som.interpreter.objectstorage;

import com.oracle.truffle.api.CompilerAsserts;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.TruffleCompiler;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.CachedSlotRead;
import som.interpreter.nodes.dispatch.CachedSlotRead.DoubleSlotReadSet;
import som.interpreter.nodes.dispatch.CachedSlotRead.DoubleSlotReadSetOrUnset;
import som.interpreter.nodes.dispatch.CachedSlotRead.LongSlotReadSet;
import som.interpreter.nodes.dispatch.CachedSlotRead.LongSlotReadSetOrUnset;
import som.interpreter.nodes.dispatch.CachedSlotRead.ObjectSlotRead;
import som.interpreter.nodes.dispatch.CachedSlotRead.SlotAccess;
import som.interpreter.nodes.dispatch.CachedSlotRead.UnwrittenSlotRead;
import som.interpreter.nodes.dispatch.CachedSlotWrite;
import som.interpreter.nodes.dispatch.CachedSlotWrite.DoubleSlotWriteSet;
import som.interpreter.nodes.dispatch.CachedSlotWrite.DoubleSlotWriteSetOrUnset;
import som.interpreter.nodes.dispatch.CachedSlotWrite.LongSlotWriteSet;
import som.interpreter.nodes.dispatch.CachedSlotWrite.LongSlotWriteSetOrUnset;
import som.interpreter.nodes.dispatch.CachedSlotWrite.ObjectSlotWrite;
import som.interpreter.nodes.dispatch.CachedSlotWrite.UnwrittenSlotWrite;
import som.interpreter.nodes.dispatch.DispatchGuard.CheckSObject;
import som.interpreter.objectstorage.StorageAccessor.AbstractObjectAccessor;
import som.interpreter.objectstorage.StorageAccessor.AbstractPrimitiveAccessor;
import som.vm.constants.Nil;
import som.vmobjects.SObject;


/**
 * A <code>StorageLocation</code> represents the element of an
 * {@link ObjectLayout} that binds a {@link SlotDefinition slot} to a specific
 * memory location within an object.
 *
 * <p>
 * <code>StorageLocation</code> provides the node factories as well as slow
 * path accessor to read/write a slot.
 */
public abstract class StorageLocation {

  public static StorageLocation createForLong(final ObjectLayout layout,
      final SlotDefinition slot, final int primFieldIndex) {
    return new LongStorageLocation(layout, slot, primFieldIndex);
  }

  public static StorageLocation createForDouble(final ObjectLayout layout,
      final SlotDefinition slot, final int primFieldIndex) {
    return new DoubleStorageLocation(layout, slot, primFieldIndex);
  }

  public static StorageLocation createForObject(final ObjectLayout layout,
      final SlotDefinition slot, final int objFieldIndex) {
    return new ObjectStorageLocation(layout, slot, objFieldIndex);
  }

  protected final ObjectLayout   layout;
  protected final SlotDefinition slot;

  protected StorageLocation(final ObjectLayout layout, final SlotDefinition slot) {
    this.layout = layout;
    this.slot = slot;
  }

  /**
   * @return true, if it is an object location, false otherwise.
   */
  public abstract boolean isObjectLocation();

  public abstract CachedSlotRead getReadNode(SlotAccess type, CheckSObject guard,
      AbstractDispatchNode nextInCache, boolean isSet);

  public abstract CachedSlotWrite getWriteNode(SlotDefinition slot,
      CheckSObject guard, AbstractDispatchNode next, boolean isSet);

  public abstract StorageAccessor getAccessor();

  /**
   * Slow-path accessor to slot.
   */
  public abstract Object read(SObject obj);

  /**
   * Slow-path accessor to slot.
   */
  public abstract void write(SObject obj, Object value);

  /**
   * Test whether the object slot has been set.
   * Unset slots return false, object slots always return true, because `nil`
   * is considered a set value.
   *
   * <p>
   * Slow-path accessor to slot.
   */
  public abstract boolean isSet(SObject obj);

  public static final class UnwrittenStorageLocation extends StorageLocation {

    public UnwrittenStorageLocation(final ObjectLayout layout,
        final SlotDefinition slot) {
      super(layout, slot);
    }

    @Override
    public boolean isObjectLocation() {
      return false;
    }

    @Override
    public CachedSlotRead getReadNode(final SlotAccess type, final CheckSObject guard,
        final AbstractDispatchNode nextInCache, final boolean isSet) {
      return new UnwrittenSlotRead(type, guard, nextInCache);
    }

    @Override
    public CachedSlotWrite getWriteNode(final SlotDefinition slot,
        final CheckSObject guard, final AbstractDispatchNode next,
        final boolean isSet) {
      return new UnwrittenSlotWrite(slot, guard, next);
    }

    /**
     * Slow-path accessor to slot.
     */
    @Override
    public Object read(final SObject obj) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      return Nil.nilObject;
    }

    /**
     * Slow-path accessor to slot.
     */
    @Override
    public void write(final SObject obj, final Object value) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      ObjectTransitionSafepoint.INSTANCE.writeUninitializedSlot(obj, slot, value);
    }

    @Override
    public boolean isSet(final SObject obj) {
      return false;
    }

    @Override
    public StorageAccessor getAccessor() {
      throw new IllegalStateException(
          "I suppose this should not happen? There's no storage location here");
    }
  }

  public static final class ObjectStorageLocation extends StorageLocation {
    private final AbstractObjectAccessor accessor;

    public ObjectStorageLocation(final ObjectLayout layout, final SlotDefinition slot,
        final int objFieldIdx) {
      super(layout, slot);
      this.accessor = StorageAccessor.getObjectAccessor(objFieldIdx);
    }

    @Override
    public boolean isObjectLocation() {
      return true;
    }

    /**
     * Slow-path accessor to slot.
     */
    @Override
    public CachedSlotRead getReadNode(final SlotAccess type,
        final CheckSObject guard, final AbstractDispatchNode nextInCache,
        final boolean isSet) {
      return new ObjectSlotRead(accessor, type, guard, nextInCache);
    }

    @Override
    public CachedSlotWrite getWriteNode(final SlotDefinition slot,
        final CheckSObject guard, final AbstractDispatchNode next,
        final boolean isSet) {
      return new ObjectSlotWrite(accessor, guard, next);
    }

    /**
     * Slow-path accessor to slot.
     */
    @Override
    public Object read(final SObject obj) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      return accessor.read(obj);
    }

    /**
     * Slow-path accessor to slot.
     */
    @Override
    public void write(final SObject obj, final Object value) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      accessor.write(obj, value);
    }

    /**
     * Slow-path accessor to slot.
     */
    @Override
    public boolean isSet(final SObject obj) {
      assert read(
          obj) != null : "null is not a valid value for an object slot, it needs to be initialized with nil.";
      return true;
    }

    @Override
    public StorageAccessor getAccessor() {
      return accessor;
    }
  }

  public abstract static class PrimitiveStorageLocation extends StorageLocation {
    protected final AbstractPrimitiveAccessor accessor;

    protected PrimitiveStorageLocation(final ObjectLayout layout,
        final SlotDefinition slot, final int primField) {
      super(layout, slot);
      accessor = StorageAccessor.getPrimitiveAccessor(primField);
    }

    @Override
    public final boolean isObjectLocation() {
      return false;
    }

    @Override
    public StorageAccessor getAccessor() {
      return accessor;
    }

    /**
     * Slow-path accessor to slot.
     */
    @Override
    public final boolean isSet(final SObject obj) {
      return accessor.isPrimitiveSet(obj);
    }
  }

  public static final class LongStorageLocation extends PrimitiveStorageLocation {

    protected LongStorageLocation(final ObjectLayout layout,
        final SlotDefinition slot, final int primField) {
      super(layout, slot, primField);
    }

    @Override
    public CachedSlotRead getReadNode(final SlotAccess type,
        final CheckSObject guard, final AbstractDispatchNode nextInCache,
        final boolean isSet) {
      if (isSet) {
        return new LongSlotReadSet(accessor, type, guard, nextInCache);
      } else {
        return new LongSlotReadSetOrUnset(accessor, type, guard, nextInCache);
      }
    }

    @Override
    public CachedSlotWrite getWriteNode(final SlotDefinition slot,
        final CheckSObject guard, final AbstractDispatchNode next,
        final boolean isSet) {
      if (isSet) {
        return new LongSlotWriteSet(slot, accessor, guard, next);
      } else {
        return new LongSlotWriteSetOrUnset(slot, accessor, guard, next);
      }
    }

    /**
     * Slow-path accessor to slot.
     */
    @Override
    public Object read(final SObject obj) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      if (isSet(obj)) {
        return accessor.readLong(obj);
      } else {
        return Nil.nilObject;
      }
    }

    /**
     * Slow-path accessor to slot.
     */
    @Override
    public void write(final SObject obj, final Object value) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      assert value != null;
      if (value instanceof Long) {
        accessor.write(obj, (long) value);
        accessor.markPrimAsSet(obj);
      } else {
        TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized write node");
        ObjectTransitionSafepoint.INSTANCE.writeAndGeneralizeSlot(obj, slot, value);
      }
    }
  }

  public static final class DoubleStorageLocation extends PrimitiveStorageLocation {

    protected DoubleStorageLocation(final ObjectLayout layout,
        final SlotDefinition slot, final int primField) {
      super(layout, slot, primField);
    }

    @Override
    public CachedSlotRead getReadNode(final SlotAccess type,
        final CheckSObject guard, final AbstractDispatchNode nextInCache,
        final boolean isSet) {
      if (isSet) {
        return new DoubleSlotReadSet(accessor, type, guard, nextInCache);
      } else {
        return new DoubleSlotReadSetOrUnset(accessor, type, guard, nextInCache);
      }
    }

    @Override
    public CachedSlotWrite getWriteNode(final SlotDefinition slot,
        final CheckSObject guard, final AbstractDispatchNode next,
        final boolean isSet) {
      if (isSet) {
        return new DoubleSlotWriteSet(slot, accessor, guard, next);
      } else {
        return new DoubleSlotWriteSetOrUnset(slot, accessor, guard, next);
      }
    }

    /**
     * Slow-path accessor to slot.
     */
    @Override
    public Object read(final SObject obj) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      if (isSet(obj)) {
        return accessor.readDouble(obj);
      } else {
        return Nil.nilObject;
      }
    }

    @Override
    public void write(final SObject obj, final Object value) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      assert value != null;
      if (value instanceof Double) {
        accessor.write(obj, (double) value);
        accessor.markPrimAsSet(obj);
      } else {
        assert value != Nil.nilObject;
        ObjectTransitionSafepoint.INSTANCE.writeAndGeneralizeSlot(obj, slot, value);
      }
    }
  }
}
