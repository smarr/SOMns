package som.interpreter.nodes.dispatch;

import som.interpreter.objectstorage.FieldAccessorNode.AbstractReadFieldNode;
import som.vmobjects.SObject;

import com.oracle.truffle.api.frame.VirtualFrame;


public class CachedSlotAccessNode extends AbstractDispatchNode {

  @Child protected AbstractDispatchNode nextInCache;
  @Child protected AbstractReadFieldNode read; // TODO: we only got read support at the moment

  public CachedSlotAccessNode(final AbstractReadFieldNode read,
      final AbstractDispatchNode nextInCache) {
    this.read = read;
    this.nextInCache = nextInCache;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame,
      final Object[] arguments) {
    return read.read((SObject) arguments[0]);
  }

  @Override
  public final int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }
}
