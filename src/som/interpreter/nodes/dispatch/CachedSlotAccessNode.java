package som.interpreter.nodes.dispatch;

import som.interpreter.objectstorage.FieldReadNode;
import som.interpreter.objectstorage.FieldWriteNode.AbstractFieldWriteNode;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;


public abstract class CachedSlotAccessNode extends AbstractDispatchNode {

  @Child protected FieldReadNode read;

  public CachedSlotAccessNode(final FieldReadNode read) {
    this.read = read;
  }

  public abstract static class CachedSlotRead extends CachedSlotAccessNode {
    @Child protected AbstractDispatchNode nextInCache;

    private final DispatchGuard           guard;

    public CachedSlotRead(final FieldReadNode read,
        final DispatchGuard guard, final AbstractDispatchNode nextInCache) {
      super(read);
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
        CompilerDirectives.transferToInterpreter();
        return replace(nextInCache).
            executeDispatch(frame, arguments);
      }
    }

    protected abstract Object read(final VirtualFrame frame, final Object rcvr) throws InvalidAssumptionException;

    @Override
    public int lengthOfDispatchChain() {
      return 1 + nextInCache.lengthOfDispatchChain();
    }
  }

  public static final class CachedImmutableSlotRead extends CachedSlotRead {

    public CachedImmutableSlotRead(final FieldReadNode read,
        final DispatchGuard guard, final AbstractDispatchNode nextInCache) {
      super(read, guard, nextInCache);
    }

    @Override
    protected Object read(final VirtualFrame frame, final Object rcvr) throws InvalidAssumptionException {
      return read.read(frame, (SImmutableObject) rcvr);
    }
  }

  public static final class CachedMutableSlotRead extends CachedSlotRead {

    public CachedMutableSlotRead(final FieldReadNode read,
        final DispatchGuard guard, final AbstractDispatchNode nextInCache) {
      super(read, guard, nextInCache);
    }

    @Override
    protected Object read(final VirtualFrame frame, final Object rcvr) throws InvalidAssumptionException {
      return read.read(frame, (SMutableObject) rcvr);
    }
  }

  public static final class CachedSlotWrite extends AbstractDispatchNode {
    @Child protected AbstractDispatchNode   nextInCache;
    @Child protected AbstractFieldWriteNode write;

    private final DispatchGuard             guard;

    public CachedSlotWrite(final AbstractFieldWriteNode write,
        final DispatchGuard guard,
        final AbstractDispatchNode nextInCache) {
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
        CompilerDirectives.transferToInterpreter();
        return replace(nextInCache).executeDispatch(frame, arguments);
      }
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1 + nextInCache.lengthOfDispatchChain();
    }
  }
}
