package som.interpreter.nodes.dispatch;

import som.vmobjects.SObjectWithClass;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class SObjectCheckDispatchNode extends AbstractDispatchNode {

  @Child private AbstractDispatchNode nextInCache;
  @Child private AbstractDispatchNode uninitializedDispatch;

  private final TypeConditionExactProfile cachingCheck;

  public SObjectCheckDispatchNode(final AbstractDispatchNode nextInCache,
      final AbstractDispatchNode uninitializedDispatch) {
    this.nextInCache           = nextInCache;
    this.uninitializedDispatch = uninitializedDispatch;
    this.cachingCheck = new TypeConditionExactProfile(SObjectWithClass.class);
  }

  @Override
  public Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments) {
    Object rcvr = arguments[0];
    if (cachingCheck.instanceOf(rcvr)) {
      return nextInCache.executeDispatch(frame, arguments);
    } else {
      CompilerDirectives.transferToInterpreter();
      return uninitializedDispatch.executeDispatch(frame, arguments);
    }
  }

  @Override
  public int lengthOfDispatchChain() {
    return nextInCache.lengthOfDispatchChain();
  }
}
