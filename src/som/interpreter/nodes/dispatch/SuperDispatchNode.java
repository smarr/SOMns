package som.interpreter.nodes.dispatch;

import som.interpreter.nodes.ISuperReadNode;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;

/**
 * Super sends are special, they lead to a lexically defined receiver class.
 * So, it's always the cached receiver.
 */
public abstract class SuperDispatchNode extends AbstractDispatchNode {

  public static SuperDispatchNode create(final SSymbol selector,
      final ISuperReadNode superNode) {
    CompilerAsserts.neverPartOfCompilation("SuperDispatchNode.create1");
    return new UninitializedDispatchNode(selector, superNode.getHolderClass(),
        superNode.isClassSide());
  }

  private static final class UninitializedDispatchNode extends SuperDispatchNode {
    private final SSymbol selector;
    private final SSymbol holderClass;
    private final boolean classSide;

    private UninitializedDispatchNode(final SSymbol selector,
        final SSymbol holderClass, final boolean classSide) {
      this.selector    = selector;
      this.holderClass = holderClass;
      this.classSide   = classSide;
    }

    private SClass getLexicalSuperClass() {
      SClass clazz = (SClass) Universe.current().getGlobal(holderClass);
      if (classSide) {
        clazz = clazz.getSOMClass();
      }
      return (SClass) clazz.getSuperClass();
    }

    private CachedDispatchNode specialize() {
      CompilerAsserts.neverPartOfCompilation("SuperDispatchNode.create2");
      SInvokable method = getLexicalSuperClass().lookupInvokable(selector);

      if (method == null) {
        throw new RuntimeException("Currently #dnu with super sent is not yet implemented. ");
      }
      DirectCallNode superMethodNode = Truffle.getRuntime().createDirectCallNode(
          method.getCallTarget());
      return replace(new CachedDispatchNode(superMethodNode));
    }

    @Override
    public Object executeDispatch(
        final VirtualFrame frame, final Object[] arguments) {
      return specialize().
          executeDispatch(frame, arguments);
    }
  }

  private static final class CachedDispatchNode extends SuperDispatchNode {
    @Child private DirectCallNode cachedSuperMethod;

    private CachedDispatchNode(final DirectCallNode superMethod) {
      this.cachedSuperMethod = superMethod;
    }

    @Override
    public Object executeDispatch(
        final VirtualFrame frame, final Object[] arguments) {
      return cachedSuperMethod.call(frame, arguments);
    }
  }

  @Override
  public final int lengthOfDispatchChain() {
    return 1;
  }
}
