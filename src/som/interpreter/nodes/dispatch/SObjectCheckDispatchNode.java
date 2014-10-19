package som.interpreter.nodes.dispatch;

import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.utilities.BranchProfile;


public final class SObjectCheckDispatchNode extends AbstractDispatchNode {

  @Child private AbstractDispatchNode nextInCache;
  @Child private UninitializedDispatchNode uninitializedDispatch;

  private final BranchProfile uninitialized;

  public SObjectCheckDispatchNode(final AbstractDispatchNode nextInCache,
      final UninitializedDispatchNode uninitializedDispatch) {
    this.nextInCache           = nextInCache;
    this.uninitializedDispatch = uninitializedDispatch;
    this.uninitialized         = BranchProfile.create();
  }

  @Override
  public Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments) {
    Object rcvr = CompilerDirectives.unsafeCast(arguments[0], Object.class, true, true);
    if (rcvr instanceof SObject) {
      return nextInCache.executeDispatch(frame, arguments);
    } else {
      uninitialized.enter();
      return uninitializedDispatch.executeDispatch(frame, arguments);
    }
  }

  @Override
  public int lengthOfDispatchChain() {
    return nextInCache.lengthOfDispatchChain();
  }
}
