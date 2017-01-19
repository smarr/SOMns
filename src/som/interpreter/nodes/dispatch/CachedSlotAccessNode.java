package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.objectstorage.FieldReadNode;
import som.interpreter.objectstorage.FieldWriteNode.AbstractFieldWriteNode;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;
import tools.dym.Tags.ClassRead;
import tools.dym.Tags.FieldRead;
import tools.dym.Tags.FieldWrite;


public abstract class CachedSlotAccessNode extends AbstractDispatchNode {

  @Child protected FieldReadNode read;

  public CachedSlotAccessNode(final SourceSection source, final FieldReadNode read) {
    super(source);
    this.read = read;
  }

  public enum SlotAccess { FIELD_READ, CLASS_READ }

  public abstract static class CachedSlotRead extends CachedSlotAccessNode {
    @Child protected AbstractDispatchNode nextInCache;

    private final DispatchGuard           guard;
    private final SlotAccess              type;

    public CachedSlotRead(final SlotAccess type,
        final FieldReadNode read,
        final DispatchGuard guard, final AbstractDispatchNode nextInCache) {
      super(nextInCache.sourceSection, read);
      this.type        = type;
      this.guard       = guard;
      this.nextInCache = nextInCache;
      assert nextInCache != null;
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object[] arguments) {
      try {
        // TODO: make sure this cast is always eliminated, otherwise, we need two versions mut/immut
        if (guard.entryMatches(arguments[0])) {
          return read(frame, arguments[0]);
        } else {
          return nextInCache.executeDispatch(frame, arguments);
        }
      } catch (InvalidAssumptionException e) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return replace(nextInCache).
            executeDispatch(frame, arguments);
      }
    }

    protected abstract Object read(final VirtualFrame frame, final Object rcvr) throws InvalidAssumptionException;

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
  }

  public static final class CachedImmutableSlotRead extends CachedSlotRead {

    public CachedImmutableSlotRead(final SlotAccess type,
        final FieldReadNode read,
        final DispatchGuard guard, final AbstractDispatchNode nextInCache) {
      super(type, read, guard, nextInCache);
    }

    @Override
    protected Object read(final VirtualFrame frame, final Object rcvr) throws InvalidAssumptionException {
      return read.read(frame, (SImmutableObject) rcvr);
    }
  }

  public static final class CachedMutableSlotRead extends CachedSlotRead {

    public CachedMutableSlotRead(final SlotAccess type,
        final FieldReadNode read,
        final DispatchGuard guard, final AbstractDispatchNode nextInCache) {
      super(type, read, guard, nextInCache);
    }

    @Override
    protected Object read(final VirtualFrame frame, final Object rcvr) throws InvalidAssumptionException {
      return read.read(frame, (SMutableObject) rcvr);
    }
  }

  public static class CachedSlotWrite extends AbstractDispatchNode {
    @Child protected AbstractDispatchNode   nextInCache;
    @Child protected AbstractFieldWriteNode write;

    protected final DispatchGuard           guard;

    public CachedSlotWrite(final AbstractFieldWriteNode write,
        final DispatchGuard guard,
        final AbstractDispatchNode nextInCache) {
      super(nextInCache.getSourceSection());
      this.write = write;
      this.guard = guard;
      this.nextInCache = nextInCache;
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object[] arguments) {
      try {
        if (guard.entryMatches(arguments[0])) {
          return write.write((SMutableObject) arguments[0], arguments[1]);
        } else {
          return nextInCache.executeDispatch(frame, arguments);
        }
      } catch (InvalidAssumptionException e) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return replace(nextInCache).executeDispatch(frame, arguments);
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
  }
}
