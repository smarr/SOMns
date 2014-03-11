package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.CallNode;


public class CachedDispatchSimpleCheckNode extends AbstractDispatchNode {

  @Child private CallNode cachedMethod;
  private final Class<?>     expectedClass;
  @Child private AbstractDispatchNode nextInCache;

  public CachedDispatchSimpleCheckNode(final Class<?> rcvrClass,
      final SInvokable method, final AbstractDispatchNode nextInCache) {
    CallTarget methodCallTarget = method.getCallTarget();
    CallNode   cachedMethod     = Truffle.getRuntime().createCallNode(methodCallTarget);

    this.cachedMethod  = adoptChild(cachedMethod);
    this.expectedClass = rcvrClass;
    this.nextInCache   = adoptChild(nextInCache);
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final SArguments arguments) {
    if (arguments.getReceiver().getClass() == expectedClass) {
      return cachedMethod.call(frame.pack(), arguments);
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }
}
