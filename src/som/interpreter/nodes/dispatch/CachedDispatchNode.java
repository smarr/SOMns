package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.CallNode;


public class CachedDispatchNode extends AbstractDispatchNode {

  @Child private CallNode             cachedMethod;
  @Child private ClassCheckNode       classCheck;
  @Child private AbstractDispatchNode nextInCache;

  public CachedDispatchNode(final SClass rcvrClass, final SMethod method,
      final AbstractDispatchNode nextInCache, final Universe universe) {
    CallTarget methodCallTarget = method.getCallTarget();
    CallNode   cachedMethod     = Truffle.getRuntime().createCallNode(methodCallTarget);

    this.cachedMethod = adoptChild(cachedMethod);
    this.classCheck   = adoptChild(ClassCheckNode.create(rcvrClass, universe));
    this.nextInCache  = adoptChild(nextInCache);
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final SArguments arguments) {
    if (classCheck.execute(arguments.getReceiver())) {
      return cachedMethod.call(frame.pack(), arguments);
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }
}
