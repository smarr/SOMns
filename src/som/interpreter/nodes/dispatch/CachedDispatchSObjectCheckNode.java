package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.CallNode;


public class CachedDispatchSObjectCheckNode extends AbstractDispatchNode {

  @Child private CallNode             cachedMethod;
  @Child private AbstractDispatchNode nextInCache;

  private final SClass                expectedClass;

  public CachedDispatchSObjectCheckNode(final SClass rcvrClass, final SInvokable method,
      final AbstractDispatchNode nextInCache, final Universe universe) {
    CallTarget methodCallTarget = method.getCallTarget();
    CallNode   cachedMethod     = Truffle.getRuntime().createCallNode(methodCallTarget);

    this.cachedMethod = adoptChild(cachedMethod);
    this.nextInCache  = adoptChild(nextInCache);
    this.expectedClass = rcvrClass;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final SArguments arguments) {
    SObject rcvr = CompilerDirectives.unsafeCast(arguments.getReceiver(), SObject.class, true);
    if (rcvr.getSOMClass(null) == expectedClass) {
      return cachedMethod.call(frame.pack(), arguments);
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }
}
