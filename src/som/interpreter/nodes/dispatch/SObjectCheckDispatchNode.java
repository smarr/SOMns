package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.vmobjects.SObject;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.utilities.BranchProfile;


public class SObjectCheckDispatchNode extends AbstractDispatchNode {

  @Child private AbstractDispatchNode nextInCache;
  @Child private UninitializedDispatchNode uninitializedDispatch;

  private final BranchProfile uninitialized;


  public SObjectCheckDispatchNode(final AbstractDispatchNode nextInCache,
      final UninitializedDispatchNode uninitializedDispatch) {
    this.nextInCache = adoptChild(nextInCache);
    this.uninitializedDispatch = adoptChild(uninitializedDispatch);
    this.uninitialized = new BranchProfile();
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final SArguments arguments) {
    if (arguments.getReceiver() instanceof SObject) {
      return nextInCache.executeDispatch(frame, arguments);
    } else {
      uninitialized.enter();
      return uninitializedDispatch.executeDispatch(frame, arguments);
    }
  }
}
