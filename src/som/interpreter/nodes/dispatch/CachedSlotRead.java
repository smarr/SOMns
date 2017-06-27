package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.profiles.IntValueProfile;

import som.interpreter.nodes.dispatch.DispatchGuard.CheckSObject;
import som.interpreter.objectstorage.StorageAccessor.AbstractObjectAccessor;
import som.interpreter.objectstorage.StorageAccessor.AbstractPrimitiveAccessor;
import som.vm.constants.Nil;
import som.vmobjects.SObject;
import tools.dym.Tags.ClassRead;
import tools.dym.Tags.FieldRead;


/**
 * Field read operations are modeled as normal message sends in Newspeak.
 * As a consequence, we use <code>CachedSlotRead</code> to handle some of the
 * variability encountered for field reads.
 *
 * <p>Currently, we handle here the cases for unwritten object slots, slots that
 * contain object or primitive values, as well as the distinction for primitive
 * slots of whether they have been always set to a value before or not.
 * This allows for a small optimization of handling the bit that indicates for
 * primitive slot whether it is set to `nil` or an actual value.
 */
public abstract class CachedSlotRead extends AbstractDispatchNode {
  @Child protected AbstractDispatchNode nextInCache;

  protected final CheckSObject guard;
  protected final SlotAccess   type;

  public CachedSlotRead(final SlotAccess type, final CheckSObject guard,
      final AbstractDispatchNode nextInCache) {
    super(nextInCache.sourceSection);
    this.type        = type;
    this.guard       = guard;
    this.nextInCache = nextInCache;
    assert nextInCache != null;
  }

  @Override
  public Object executeDispatch(final Object[] arguments) {
    try {
      if (guard.entryMatches(arguments[0])) {
        return read(guard.cast(arguments[0]));
      } else {
        return nextInCache.executeDispatch(arguments);
      }
    } catch (InvalidAssumptionException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return replace(nextInCache).
          executeDispatch(arguments);
    }
  }

  public abstract Object read(SObject rcvr);

  @Override
  public int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == ClassRead.class) {
      return type == SlotAccess.CLASS_READ;
    } else if (tag == FieldRead.class) {
      return type == SlotAccess.FIELD_READ;
    } else {
      return super.isTaggedWith(tag);
    }
  }

  public enum SlotAccess { FIELD_READ, CLASS_READ }

  public static final class UnwrittenSlotRead extends CachedSlotRead {

    public UnwrittenSlotRead(final SlotAccess type, final CheckSObject guard,
        final AbstractDispatchNode nextInCache) {
      super(type, guard, nextInCache);
    }

    @Override
    public Object read(final SObject rcvr) {
      return Nil.nilObject;
    }
  }

  public static final class ObjectSlotRead extends CachedSlotRead {
    private final AbstractObjectAccessor accessor;

    public ObjectSlotRead(final AbstractObjectAccessor accessor,
        final SlotAccess type, final CheckSObject guard,
        final AbstractDispatchNode nextInCache) {
      super(type, guard, nextInCache);
      this.accessor = accessor;
    }

    @Override
    public Object read(final SObject rcvr) {
      return accessor.read(rcvr);
    }
  }

  private abstract static class PrimSlotRead extends CachedSlotRead {
    protected final AbstractPrimitiveAccessor accessor;
    protected final IntValueProfile primMarkProfile;

    PrimSlotRead(final AbstractPrimitiveAccessor accessor,
        final SlotAccess type, final CheckSObject guard,
        final AbstractDispatchNode nextInCache) {
      super(type, guard, nextInCache);
      this.accessor = accessor;
      this.primMarkProfile = IntValueProfile.createIdentityProfile();
    }
  }

  public static final class LongSlotReadSetOrUnset extends PrimSlotRead {

    public LongSlotReadSetOrUnset(final AbstractPrimitiveAccessor accessor,
        final SlotAccess type, final CheckSObject guard,
        final AbstractDispatchNode nextInCache) {
      super(accessor, type, guard, nextInCache);
    }

    @Override
    public Object read(final SObject rcvr) {
      if (accessor.isPrimitiveSet(rcvr, primMarkProfile)) {
        return accessor.readLong(rcvr);
      } else {
        return Nil.nilObject;
      }
    }
  }

  public static final class LongSlotReadSet extends PrimSlotRead {

    public LongSlotReadSet(final AbstractPrimitiveAccessor accessor,
        final SlotAccess type, final CheckSObject guard,
        final AbstractDispatchNode nextInCache) {
      super(accessor, type, guard, nextInCache);
    }

    @Override
    public Object read(final SObject rcvr) {
      if (accessor.isPrimitiveSet(rcvr, primMarkProfile)) {
        return accessor.readLong(rcvr);
      } else {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        replace(new LongSlotReadSetOrUnset(accessor, type, guard, nextInCache));
        return Nil.nilObject;
      }
    }
  }

  public static final class DoubleSlotReadSetOrUnset extends PrimSlotRead {

    public DoubleSlotReadSetOrUnset(final AbstractPrimitiveAccessor accessor,
        final SlotAccess type, final CheckSObject guard,
        final AbstractDispatchNode nextInCache) {
      super(accessor, type, guard, nextInCache);
    }

    @Override
    public Object read(final SObject rcvr) {
      if (accessor.isPrimitiveSet(rcvr, primMarkProfile)) {
        return accessor.readDouble(rcvr);
      } else {
        return Nil.nilObject;
      }
    }
  }

  public static final class DoubleSlotReadSet extends PrimSlotRead {

    public DoubleSlotReadSet(final AbstractPrimitiveAccessor accessor,
        final SlotAccess type, final CheckSObject guard,
        final AbstractDispatchNode nextInCache) {
      super(accessor, type, guard, nextInCache);
    }

    @Override
    public Object read(final SObject rcvr) {
      if (accessor.isPrimitiveSet(rcvr, primMarkProfile)) {
        return accessor.readDouble(rcvr);
      } else {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        replace(new DoubleSlotReadSetOrUnset(accessor, type, guard, nextInCache));
        return Nil.nilObject;
      }
    }
  }
}
