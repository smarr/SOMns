package som.interpreter.nodes.dispatch;

import som.interpreter.nodes.SlotAccessNode;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractWriteFieldNode;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.frame.VirtualFrame;


public final class CachedSlotAccessNode extends AbstractDispatchNode {

  @Child protected SlotAccessNode access;
  @Child protected AbstractDispatchNode nextInCache;

  private final SClass rcvrClass;

  public CachedSlotAccessNode(final SClass rcvrClass,
      final SlotAccessNode access,
      final AbstractDispatchNode nextInCache) {
    this.access = access;
    this.nextInCache = nextInCache;
    this.rcvrClass = rcvrClass;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame,
      final Object[] arguments) {
    assert arguments[0] instanceof SObject;
    SObject rcvr = (SObject) arguments[0];

    if (rcvr.getSOMClass() == rcvrClass) {
      return access.doRead(frame, rcvr);
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }

  public static final class CachedSlotWriteNode extends AbstractDispatchNode {

    private final SClass rcvrClass;
    @Child protected AbstractDispatchNode nextInCache;

    @Child protected AbstractWriteFieldNode write;

    public CachedSlotWriteNode(final SClass rcvrClass, final AbstractWriteFieldNode write,
        final AbstractDispatchNode nextInCache) {
      this.rcvrClass   = rcvrClass;
      this.write       = write;
      this.nextInCache = nextInCache;
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object[] arguments) {
      assert arguments[0] instanceof SObject;
      SObject rcvr = (SObject) arguments[0];

      if (rcvr.getSOMClass() == rcvrClass) {
        return write.write(rcvr, arguments[1]);
      } else {
        return nextInCache.executeDispatch(frame, arguments);
      }
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1 + nextInCache.lengthOfDispatchChain();
    }
  }
}
