package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.profiles.IntValueProfile;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.TruffleCompiler;
import som.interpreter.nodes.dispatch.DispatchGuard.CheckSObject;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.interpreter.objectstorage.StorageAccessor.AbstractObjectAccessor;
import som.interpreter.objectstorage.StorageAccessor.AbstractPrimitiveAccessor;
import som.vmobjects.SObject;
import som.vmobjects.SObject.SMutableObject;
import tools.dym.Tags.FieldWrite;


/**
 * <code>CachedSlotWrite</code> mirrors the functionality of
 * {@link CachedSlotRead} and embeds the field writing operations into a
 * dispatch chain. See {@link CachedSlotRead} for more details on the design.
 */
public abstract class CachedSlotWrite extends AbstractDispatchNode {
  @Child protected AbstractDispatchNode nextInCache;

  protected final CheckSObject guard;

  public CachedSlotWrite(final CheckSObject guard,
      final AbstractDispatchNode nextInCache) {
    super(nextInCache.getSourceSection());
    this.guard = guard;
    this.nextInCache = nextInCache;
  }

  public abstract void doWrite(SObject obj, Object value);

  @Override
  public final Object executeDispatch(final Object[] arguments) {
    try {
      if (guard.entryMatches(arguments[0])) {
        doWrite((SMutableObject) arguments[0], arguments[1]);
        return arguments[1];
      } else {
        return nextInCache.executeDispatch(arguments);
      }
    } catch (InvalidAssumptionException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return replace(nextInCache).executeDispatch(arguments);
    }
  }

  @Override
  protected final boolean isTaggedWith(final Class<?> tag) {
    if (tag == FieldWrite.class) {
      return true;
    } else {
      return super.isTaggedWith(tag);
    }
  }

  @Override
  public final int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }

  public static final class UnwrittenSlotWrite extends CachedSlotWrite {
    private final SlotDefinition slot;

    public UnwrittenSlotWrite(final SlotDefinition slot, final CheckSObject guard,
        final AbstractDispatchNode nextInCache) {
      super(guard, nextInCache);
      this.slot = slot;
    }

    @Override
    public void doWrite(final SObject obj, final Object value) {
      TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized write node");
      ObjectTransitionSafepoint.INSTANCE.writeUninitializedSlot(obj, slot, value);
    }
  }

  public static final class ObjectSlotWrite extends CachedSlotWrite {
    private final AbstractObjectAccessor accessor;

    public ObjectSlotWrite(final AbstractObjectAccessor accessor,
        final CheckSObject guard, final AbstractDispatchNode nextInCache) {
      super(guard, nextInCache);
      this.accessor = accessor;
    }

    @Override
    public void doWrite(final SObject obj, final Object value) {
      accessor.write(obj, value);
    }
  }

  private abstract static class PrimSlotWrite extends CachedSlotWrite {
    protected final AbstractPrimitiveAccessor accessor;
    protected final SlotDefinition            slot;
    protected final IntValueProfile           primMarkProfile;

    PrimSlotWrite(final SlotDefinition slot,
        final AbstractPrimitiveAccessor accessor, final CheckSObject guard,
        final AbstractDispatchNode nextInCache) {
      super(guard, nextInCache);
      this.accessor = accessor;
      this.slot = slot;
      this.primMarkProfile = IntValueProfile.createIdentityProfile();
    }
  }

  public static final class LongSlotWriteSetOrUnset extends PrimSlotWrite {

    public LongSlotWriteSetOrUnset(final SlotDefinition slot,
        final AbstractPrimitiveAccessor accessor, final CheckSObject guard,
        final AbstractDispatchNode nextInCache) {
      super(slot, accessor, guard, nextInCache);
    }

    @Override
    public void doWrite(final SObject obj, final Object value) {
      if (value instanceof Long) {
        accessor.write(obj, (long) value);
        accessor.markPrimAsSet(obj, primMarkProfile);
      } else {
        TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized write node");
        ObjectTransitionSafepoint.INSTANCE.writeAndGeneralizeSlot(obj, slot, value);
      }
    }
  }

  public static final class LongSlotWriteSet extends PrimSlotWrite {

    public LongSlotWriteSet(final SlotDefinition slot,
        final AbstractPrimitiveAccessor accessor, final CheckSObject guard,
        final AbstractDispatchNode nextInCache) {
      super(slot, accessor, guard, nextInCache);
    }

    @Override
    public void doWrite(final SObject obj, final Object value) {
      if (value instanceof Long) {
        accessor.write(obj, (long) value);
        if (!accessor.isPrimitiveSet(obj, primMarkProfile)) {
          CompilerDirectives.transferToInterpreterAndInvalidate();
          accessor.markPrimAsSet(obj);

          // fall back to LongSlotWriteSetOrUnset
          replace(new LongSlotWriteSetOrUnset(slot, accessor, guard, nextInCache));
        }
      } else {
        TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized write node");
        ObjectTransitionSafepoint.INSTANCE.writeAndGeneralizeSlot(obj, slot, value);
      }
    }
  }

  public static final class DoubleSlotWriteSetOrUnset extends PrimSlotWrite {

    public DoubleSlotWriteSetOrUnset(final SlotDefinition slot,
        final AbstractPrimitiveAccessor accessor, final CheckSObject guard,
        final AbstractDispatchNode nextInCache) {
      super(slot, accessor, guard, nextInCache);
    }

    @Override
    public void doWrite(final SObject obj, final Object value) {
      if (value instanceof Double) {
        accessor.write(obj, (double) value);
        accessor.markPrimAsSet(obj, primMarkProfile);
      } else {
        TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized write node");
        ObjectTransitionSafepoint.INSTANCE.writeAndGeneralizeSlot(obj, slot, value);
      }
    }
  }

  public static final class DoubleSlotWriteSet extends PrimSlotWrite {

    public DoubleSlotWriteSet(final SlotDefinition slot,
        final AbstractPrimitiveAccessor accessor, final CheckSObject guard,
        final AbstractDispatchNode nextInCache) {
      super(slot, accessor, guard, nextInCache);
    }

    @Override
    public void doWrite(final SObject obj, final Object value) {
      if (value instanceof Double) {
        accessor.write(obj, (double) value);
        if (!accessor.isPrimitiveSet(obj, primMarkProfile)) {
          CompilerDirectives.transferToInterpreterAndInvalidate();
          accessor.markPrimAsSet(obj);

          // fall back to LongSlotWriteSetOrUnset
          replace(new DoubleSlotWriteSetOrUnset(slot, accessor, guard, nextInCache));
        }
      } else {
        TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized write node");
        ObjectTransitionSafepoint.INSTANCE.writeAndGeneralizeSlot(obj, slot, value);
      }
    }
  }
}
