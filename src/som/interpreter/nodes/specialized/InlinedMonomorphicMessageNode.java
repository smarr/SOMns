package som.interpreter.nodes.specialized;

import som.interpreter.Method;
import som.interpreter.nodes.ExpressionNode;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.nodes.FrameFactory;
import com.oracle.truffle.api.nodes.InlinedCallSite;

public class InlinedMonomorphicMessageNode extends AbstractInlinedMessageNode
  implements InlinedCallSite {

  public InlinedMonomorphicMessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments, final SSymbol selector,
      final Universe universe, final SClass rcvrClass,
      final SInvokable invokable,
      final FrameFactory frameFactory,
      final Method inlinedMethod, final ExpressionNode methodBody) {
    super(receiver, arguments, selector, universe, rcvrClass, invokable,
        frameFactory, inlinedMethod, methodBody);
  }

  @Override
  public CallTarget getCallTarget() {
    return invokable.getCallTarget();
  }
}
