package som.interpreter.nodes.dispatch;

import som.interpreter.nodes.SlotAccessNode;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.frame.VirtualFrame;


public class CachedSlotAccessNode extends AbstractDispatchNode {

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
  public final int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }
}
