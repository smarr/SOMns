package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.PreevaluatedExpression;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeUtil;


public final class InlinedDispatchNode extends AbstractDispatchNode {
  @Child private ClassCheckNode         classCheck;
  @Child private AbstractDispatchNode   nextInCache;
  @Child private ExpressionNode expr;

  public static InlinedDispatchNode create(final SClass rcvrClass,
      final SMethod method, final AbstractDispatchNode nextInCache,
      final Universe universe) {
    PreevaluatedExpression inlined = (PreevaluatedExpression)
        NodeUtil.cloneNode(method.getInvokable().getUninitializedBody().
            getFirstMethodBodyNode());

    return new InlinedDispatchNode(inlined,
        ClassCheckNode.create(rcvrClass, universe), nextInCache);
  }

  private InlinedDispatchNode(final PreevaluatedExpression inlined,
      final ClassCheckNode classCheck,
      final AbstractDispatchNode nextInCache) {
    this.expr        = adoptChild((ExpressionNode) inlined);
    this.classCheck  = adoptChild(classCheck);
    this.nextInCache = adoptChild(nextInCache);
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final SArguments arguments) {
    if (classCheck.execute(arguments.getReceiver())) {
      return ((PreevaluatedExpression) expr).executePreEvaluated(frame, arguments.getReceiver(),
          arguments.getArguments());
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }
}
