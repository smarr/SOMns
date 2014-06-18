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
      final ISuperReadNode superNode, final boolean executesEnforced) {
    CompilerAsserts.neverPartOfCompilation();
    return create(selector, superNode.getSuperClass(), executesEnforced);
  }

  public static SuperDispatchNode create(final SSymbol selector,
      final SClass lookupClass, final boolean executesEnforced) {
    CompilerAsserts.neverPartOfCompilation();
    SInvokable method = lookupClass.lookupInvokable(selector);

    if (method == null) {
      throw new RuntimeException("Currently #dnu with super sent is not yet implemented. ");
    }
    DirectCallNode superMethodNode = Truffle.getRuntime().createDirectCallNode(
        method.getCallTarget());
    return new SuperDispatchNode(superMethodNode, executesEnforced);
  }

  @Child private DirectCallNode cachedSuperMethod;

  private SuperDispatchNode(final DirectCallNode superMethod, final boolean executesEnforced) {
    super(executesEnforced);
    this.cachedSuperMethod = superMethod;
  }

  @Override
  public Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments) {
    SObject domain = SArguments.domain(frame);
    return cachedSuperMethod.call(frame, SArguments.createSArguments(domain, executesEnforced, arguments));
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1;
  }
}
