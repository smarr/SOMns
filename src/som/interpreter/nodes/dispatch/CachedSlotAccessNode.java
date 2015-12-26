package som.interpreter.nodes.dispatch;

import som.interpreter.nodes.SlotAccessNode;
import som.interpreter.objectstorage.ClassFactory;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractWriteFieldNode;
import som.interpreter.objectstorage.ObjectLayout;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithClass;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;


public class CachedSlotAccessNode extends AbstractDispatchNode {

  @Child protected SlotAccessNode access;

  public CachedSlotAccessNode(final SlotAccessNode access) {
    this.access = access;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame,
      final Object[] arguments) {
    assert arguments[0] instanceof SObject;
    SObject rcvr = (SObject) arguments[0];
    return access.doRead(frame, rcvr);
  }

  public static final class CheckedCachedSlotAccessNode extends CachedSlotAccessNode {
    @Child protected AbstractDispatchNode nextInCache;

    private final ObjectLayout expectedLayout;

    public CheckedCachedSlotAccessNode(final ObjectLayout rcvrLayout,
      final SlotAccessNode access,
      final AbstractDispatchNode nextInCache) {
      super(access);
      this.nextInCache = nextInCache;
      this.expectedLayout = rcvrLayout;
    }

    // TODO: when we have this layout check here, do we need it later in the access node?
    //       do we need to move the logic for dropping specializations for old layouts here?

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object[] arguments) {
      try {
        expectedLayout.checkIsLatest();

        if (arguments[0] instanceof SObject &&
            ((SObject) arguments[0]).getObjectLayout() == expectedLayout) {
          return access.doRead(frame, ((SObject) arguments[0]));
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

  @Override
  public int lengthOfDispatchChain() { return 1; }

  public static class CachedSlotWriteNode extends AbstractDispatchNode {

    @Child protected AbstractWriteFieldNode write;

    public CachedSlotWriteNode(final AbstractWriteFieldNode write) {
      this.write = write;
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object[] arguments) {
      assert arguments[0] instanceof SObject;
      SObject rcvr = (SObject) arguments[0];
      return write.write(rcvr, arguments[1]);
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1;
    }
  }

  public static final class CheckedCachedSlotWriteNode extends CachedSlotWriteNode {
    private final ClassFactory rcvrFactory;
    @Child protected AbstractDispatchNode nextInCache;

    public CheckedCachedSlotWriteNode(final ClassFactory rcvrFactory,
        final AbstractWriteFieldNode write,
        final AbstractDispatchNode nextInCache) {
      super(write);
      this.rcvrFactory = rcvrFactory;
      this.nextInCache = nextInCache;
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object[] arguments) {
      assert arguments[0] instanceof SObjectWithClass;
      SObjectWithClass rcvr = (SObjectWithClass) arguments[0];

      if (rcvr.getFactory() == rcvrFactory) {
        assert arguments[0] instanceof SObject;
        return write.write((SObject) rcvr, arguments[1]);
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
