package som.interpreter.nodes.dispatch;

import som.interpreter.objectstorage.ClassFactory;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithoutFields;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;


public final class CachedDnuSObjectCheckNode extends AbstractCachedDnuNode {
  private final ClassFactory expectedClassFactory;

  public CachedDnuSObjectCheckNode(final SClass rcvrClass,
      final SSymbol selector, final AbstractDispatchNode nextInCache) {
    super(rcvrClass, selector, nextInCache);
    expectedClassFactory = rcvrClass.getClassFactory();
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    SObjectWithoutFields rcvr = (SObjectWithoutFields) arguments[0];

    if (rcvr.getFactory() == expectedClassFactory) {
      return performDnu(frame, arguments, rcvr);
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }
}
