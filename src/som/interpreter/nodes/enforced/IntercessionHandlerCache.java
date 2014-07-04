package som.interpreter.nodes.enforced;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.SArguments;
import som.interpreter.nodes.specialized.whileloops.WhileCache.AbstractWhileDispatch;
import som.vm.Universe;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;


public final class IntercessionHandlerCache {
  public static final int INLINE_CACHE_SIZE = 6;

  public static AbstractIntercessionHandlerDispatch create(
      final String intercessionHandler, final boolean executesEnforced) {
    CompilerAsserts.neverPartOfCompilation();
    return new UninitializedDispatch(Universe.current().symbolFor(intercessionHandler), executesEnforced, 0);
  }

  public abstract static class AbstractIntercessionHandlerDispatch extends Node {
    protected final boolean executesEnforced;
    protected final int depth;

    public AbstractIntercessionHandlerDispatch(final boolean executesEnforced, final int depth) {
      this.executesEnforced = executesEnforced;
      this.depth            = depth;
    }

    public abstract Object executeDispatch(VirtualFrame frame,
        SObject rcvrDomain, Object[] arguments);
  }

  private static final class UninitializedDispatch extends AbstractIntercessionHandlerDispatch {
    private final SSymbol intercessionHandlerSelector;

    public UninitializedDispatch(final SSymbol intercessionHandler,
        final boolean executesEnforced, final int depth) {
      super(executesEnforced, depth);
      intercessionHandlerSelector = intercessionHandler;
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final SObject rcvrDomain, final Object[] arguments) {
      transferToInterpreterAndInvalidate("Initialize a dispatch node.");

      SInvokable handler = rcvrDomain.getSOMClass(Universe.current()).
          lookupInvokable(intercessionHandlerSelector);

      if (depth < INLINE_CACHE_SIZE) {
        CachedDispatch specialized = new CachedDispatch(rcvrDomain, handler,
            executesEnforced, depth);
        return replace(specialized).
            executeDispatch(frame, rcvrDomain, arguments);
      }

      AbstractIntercessionHandlerDispatch headNode = determineChainHead();
      GenericDispatch generic = new GenericDispatch(intercessionHandlerSelector,
          executesEnforced);
      return headNode.replace(generic).
          executeDispatch(frame, rcvrDomain, arguments);
    }

    private AbstractIntercessionHandlerDispatch determineChainHead() {
      Node i = this;
      while (i.getParent() instanceof AbstractWhileDispatch) {
        i = i.getParent();
      }
      return (AbstractIntercessionHandlerDispatch) i;
    }
  }

  public static final class CachedDispatch extends AbstractIntercessionHandlerDispatch {
    private final SObject rcvrDomain;

    @Child private DirectCallNode dispatch;
    @Child private AbstractIntercessionHandlerDispatch next;

    public CachedDispatch(final SObject rcvrDomain,
        final SInvokable intercessionHandler, final boolean executesEnforced,
        final int depth) {
      super(executesEnforced, depth);
      this.rcvrDomain = rcvrDomain;
      this.next = new UninitializedDispatch(intercessionHandler.getSignature(),
          executesEnforced, depth + 1);
      this.dispatch = Truffle.getRuntime().createDirectCallNode(
          intercessionHandler.getCallTarget());
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final SObject rcvrDomain, final Object[] arguments) {
      if (this.rcvrDomain == rcvrDomain) {
        return dispatch.call(frame, arguments);

      } else {
        return next.executeDispatch(frame, rcvrDomain, arguments);
      }
    }
  }

  public static final class GenericDispatch extends AbstractIntercessionHandlerDispatch {
    private final Universe universe;
    private final SSymbol intercessionHandlerSelector;

    public GenericDispatch(final SSymbol intercessionHandlerSelector,
        final boolean executesEnforced) {
      super(executesEnforced, 0);
      this.universe = Universe.current();
      this.intercessionHandlerSelector = intercessionHandlerSelector;
    }


    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final SObject rcvrDomain, final Object[] arguments) {
      CompilerAsserts.neverPartOfCompilation(); // no caching, direct invokes, no loop count reporting...

      SObject currentDomain = SArguments.domain(frame);
      SInvokable handler = rcvrDomain.getSOMClass(universe).
          lookupInvokable(intercessionHandlerSelector);
      return handler.invoke(currentDomain, false, arguments);
    }

  }
}
