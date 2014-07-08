package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.interpreter.nodes.ISuperReadNode;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;

/**
 * Super sends are special, they lead to a lexically defined receiver class.
 * So, it's always the cached receiver.
 */
public final class SuperDispatchNode extends AbstractDispatchNode {

  public static SuperDispatchNode create(final SSymbol selector,
      final ISuperReadNode superNode) {
    CompilerAsserts.neverPartOfCompilation("SuperDispatchNode.create1");
    return create(selector, superNode.getSuperClass());
  }

  public static SuperDispatchNode create(final SSymbol selector,
      final SClass lookupClass) {
    CompilerAsserts.neverPartOfCompilation("SuperDispatchNode.create2");
    SInvokable method = lookupClass.lookupInvokable(selector);

    if (method == null) {
      throw new RuntimeException("Currently #dnu with super sent is not yet implemented. ");
    }
    DirectCallNode superMethodNode = Truffle.getRuntime().createDirectCallNode(
        method.getCallTarget());
    return new SuperDispatchNode(superMethodNode);
  }

  @Child private DirectCallNode cachedSuperMethod;

  private SuperDispatchNode(final DirectCallNode superMethod) {
    this.cachedSuperMethod = superMethod;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final SObject domain,
      final boolean enforced, final Object[] arguments) {
    return cachedSuperMethod.call(frame, SArguments.createSArguments(domain, enforced, arguments));
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1;
  }
}
