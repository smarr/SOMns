package som.interpreter.nodes.dispatch;

import som.interpreter.objectstorage.FieldAccessorNode.AbstractReadFieldNode;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.frame.VirtualFrame;


public class CachedSlotAccessNode extends AbstractDispatchNode {

  @Child protected AbstractDispatchNode nextInCache;
  @Child protected AbstractReadFieldNode read; // TODO: we only got read support at the moment
  private final SClass rcvrClass;

  public CachedSlotAccessNode(final SClass rcvrClass,
      final AbstractReadFieldNode read, final AbstractDispatchNode nextInCache) {
    this.read = read;
    this.nextInCache = nextInCache;
    this.rcvrClass = rcvrClass;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame,
      final Object[] arguments) {
    assert arguments[0] instanceof SObject;
    SObject rcvr = (SObject) arguments[0];

    if (rcvr.getSOMClass() == rcvrClass) {
      return read.read(rcvr);
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }

  @Override
  public final int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }
}
