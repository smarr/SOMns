package som.primitives.reflection;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.SArguments;
import som.interpreter.Types;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.PreevaluatedExpression;
import som.vm.Universe;
import som.vmobjects.SArray;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;


public abstract class AbstractSymbolDispatch extends Node {
  public static final int INLINE_CACHE_SIZE = 6;

  public static AbstractSymbolDispatch create(final boolean alwaysEnforced) {
    return new UninitializedDispatchNode(alwaysEnforced);
  }

  protected final boolean alwaysEnforced;

  public AbstractSymbolDispatch(final boolean alwaysEnforced) {
    this.alwaysEnforced = alwaysEnforced;
  }

  public abstract Object executeDispatch(VirtualFrame frame, Object receiver,
      SSymbol selector, Object[] argsArr);

  public abstract int lengthOfDispatchChain();

  private static final class UninitializedDispatchNode extends AbstractSymbolDispatch {

    public UninitializedDispatchNode(final boolean alwaysEnforced) {
      super(alwaysEnforced);
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object receiver, final SSymbol selector, final Object[] argsArr) {
      transferToInterpreterAndInvalidate("Initialize a dispatch node.");

      boolean enforced = SArguments.enforced(frame);
      int chainDepth = determineChainLength();

      if (chainDepth < INLINE_CACHE_SIZE) {
        CachedDispatchNode specialized = new CachedDispatchNode(selector,
            new UninitializedDispatchNode(alwaysEnforced), enforced, alwaysEnforced);
        return replace(specialized).executeDispatch(frame, receiver, selector, argsArr);
      }

      // TODO: normally, we throw away the whole chain, and replace it with the megamorphic node...
      GenericDispatchNode generic = new GenericDispatchNode(enforced, alwaysEnforced);
      return replace(generic).executeDispatch(frame, receiver, selector, argsArr);
    }

    private int determineChainLength() {
      // Determine position in dispatch chain, i.e., size of inline cache
      Node i = this;
      int chainDepth = 0;
      while (i.getParent() instanceof AbstractSymbolDispatch) {
        i = i.getParent();
        chainDepth++;
      }
      return chainDepth;
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
        final boolean executesEnforced, final boolean alwaysEnforced) {
      super(alwaysEnforced);
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

    private final Universe universe;
    private final boolean executesEnforced;

    public GenericDispatchNode(final boolean executesEnforced, final boolean alwaysEnforced) {
      super(alwaysEnforced);
      universe = Universe.current();
      this.executesEnforced = executesEnforced;
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object receiver, final SSymbol selector, final Object[] argsArr) {
      SInvokable invokable = Types.getClassOf(receiver, universe).lookupInvokable(selector);

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
