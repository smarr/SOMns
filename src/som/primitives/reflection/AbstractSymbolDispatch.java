package som.primitives.reflection;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.SArguments;
import som.interpreter.Types;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.PreevaluatedExpression;
import som.interpreter.nodes.dispatch.DispatchChain;
import som.vmobjects.SArray;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;


public abstract class AbstractSymbolDispatch extends Node implements DispatchChain {
  public static final int INLINE_CACHE_SIZE = 6;

  public static AbstractSymbolDispatch create(final boolean alwaysEnforced) {
    return new UninitializedDispatchNode(alwaysEnforced, 0);
  }

  protected final boolean alwaysEnforced;
  protected final int depth;

  public AbstractSymbolDispatch(final boolean alwaysEnforced, final int depth) {
    this.alwaysEnforced = alwaysEnforced;
    this.depth = depth;
  }

  public abstract Object executeDispatch(VirtualFrame frame, Object receiver,
      SSymbol selector, Object[] argsArr);

  private static final class UninitializedDispatchNode extends AbstractSymbolDispatch {

    public UninitializedDispatchNode(final boolean alwaysEnforced, final int depth) {
      super(alwaysEnforced, depth);
    }

    private AbstractSymbolDispatch specialize(final boolean enforced, final SSymbol selector) {
      transferToInterpreterAndInvalidate("Initialize a dispatch node.");

      if (depth < INLINE_CACHE_SIZE) {
        CachedDispatchNode specialized = new CachedDispatchNode(selector,
            new UninitializedDispatchNode(alwaysEnforced, depth + 1),
            enforced, alwaysEnforced, depth);
        return replace(specialized);
      }

      AbstractSymbolDispatch headNode = determineChainHead();
      GenericDispatchNode generic = new GenericDispatchNode(enforced, alwaysEnforced);
      return headNode.replace(generic);
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object receiver, final SSymbol selector, final Object[] argsArr) {
      boolean enforced = SArguments.enforced(frame);
      return specialize(enforced, selector).
          executeDispatch(frame, receiver, selector, argsArr);
    }

    private AbstractSymbolDispatch determineChainHead() {
      Node i = this;
      while (i.getParent() instanceof AbstractSymbolDispatch) {
        i = i.getParent();
      }
      return (AbstractSymbolDispatch) i;
    }

    @Override
    public int lengthOfDispatchChain() {
      return 0;
    }
  }

  private static final class CachedDispatchNode extends AbstractSymbolDispatch {
    private final SSymbol selector;
    @Child private ExpressionNode cachedSend;
    @Child private AbstractSymbolDispatch nextInCache;

    public CachedDispatchNode(final SSymbol selector,
        final AbstractSymbolDispatch nextInCache,
        final boolean executesEnforced, final boolean alwaysEnforced,
        final int depth) {
      super(alwaysEnforced, depth);
      this.selector = selector;
      this.nextInCache = nextInCache;
      cachedSend = MessageSendNode.createForPerformNodes(selector, executesEnforced || alwaysEnforced);
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object receiver, final SSymbol selector, final Object[] argsArr) {
      if (this.selector == selector) {
        PreevaluatedExpression realCachedSend = CompilerDirectives.unsafeCast(cachedSend, PreevaluatedExpression.class, true);
        return realCachedSend.doPreEvaluated(frame, SArray.fromSArrayToArgArrayWithReceiver(argsArr, receiver));
      } else {
        return nextInCache.executeDispatch(frame, receiver, selector, argsArr);
      }
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1 + nextInCache.lengthOfDispatchChain();
    }
  }

  private static final class GenericDispatchNode extends AbstractSymbolDispatch {

    private final boolean executesEnforced;

    public GenericDispatchNode(final boolean executesEnforced,
        final boolean alwaysEnforced) {
      super(alwaysEnforced, 0);
      this.executesEnforced = executesEnforced;
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object receiver, final SSymbol selector, final Object[] argsArr) {
      SInvokable invokable = Types.getClassOf(receiver).lookupInvokable(selector);

      SObject domain = SArguments.domain(frame);
      Object[] args = SArguments.createSArgumentsWithReceiver(domain,
          executesEnforced || alwaysEnforced, receiver, argsArr);

      return invokable.invokeWithSArguments(args);
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1000;
    }
  }
}
