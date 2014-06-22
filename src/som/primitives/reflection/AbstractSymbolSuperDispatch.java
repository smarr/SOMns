package som.primitives.reflection;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.SArguments;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.PreevaluatedExpression;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;


public abstract class AbstractSymbolSuperDispatch extends Node {
  public static final int INLINE_CACHE_SIZE = 6;

  public static AbstractSymbolSuperDispatch create(
      final boolean executesEnforced, final boolean alwaysEnforced) {
    return new UninitializedDispatchNode(executesEnforced, alwaysEnforced);
  }

  protected final boolean executesEnforced;
  protected final boolean alwaysEnforced;

  public AbstractSymbolSuperDispatch(final boolean executesEnforced,
      final boolean alwaysEnforced) {
    this.executesEnforced = executesEnforced;
    this.alwaysEnforced   = alwaysEnforced;
  }

  public abstract Object executeDispatch(VirtualFrame frame, Object receiver,
      SSymbol selector, SClass lookupClass, Object[] argsArr);

  public abstract int lengthOfDispatchChain();

  private static final class UninitializedDispatchNode extends AbstractSymbolSuperDispatch {

    public UninitializedDispatchNode(final boolean executesEnforced,
        final boolean alwaysEnforced) {
      super(executesEnforced, alwaysEnforced);
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object receiver, final SSymbol selector, final SClass lookupClass,
        final Object[] argsArr) {
      transferToInterpreterAndInvalidate("Initialize a dispatch node.");

      boolean enforced = SArguments.enforced(frame);

      int chainDepth = determineChainLength();

      if (chainDepth < INLINE_CACHE_SIZE) {
        CachedDispatchNode specialized = new CachedDispatchNode(selector,
            lookupClass,
            new UninitializedDispatchNode(executesEnforced, alwaysEnforced),
            executesEnforced, alwaysEnforced);
        return replace(specialized).
            executeDispatch(frame, receiver, selector, lookupClass, argsArr);
      }

      // TODO: normally, we throw away the whole chain, and replace it with the megamorphic node...
      GenericDispatchNode generic = new GenericDispatchNode(enforced, alwaysEnforced);
      return replace(generic).executeDispatch(frame, receiver, selector, lookupClass, argsArr);
    }

    private int determineChainLength() {
      // Determine position in dispatch chain, i.e., size of inline cache
      Node i = this;
      int chainDepth = 0;
      while (i.getParent() instanceof AbstractSymbolSuperDispatch) {
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

  private static final class CachedDispatchNode extends AbstractSymbolSuperDispatch {
    private final SSymbol selector;
    private final SClass  lookupClass;
    @Child private ExpressionNode cachedSend;
    @Child private AbstractSymbolSuperDispatch nextInCache;

    public CachedDispatchNode(final SSymbol selector, final SClass lookupClass,
        final AbstractSymbolSuperDispatch nextInCache,
        final boolean executesEnforced, final boolean alwaysEnforced) {
      super(executesEnforced, alwaysEnforced);
      this.selector    = selector;
      this.lookupClass = lookupClass;
      this.nextInCache = nextInCache;

      cachedSend = MessageSendNode.createForPerformInSuperclassNodes(
          selector, lookupClass, executesEnforced || alwaysEnforced);
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object receiver, final SSymbol selector, final SClass lookupClass, final Object[] argsArr) {
      if (this.selector == selector && this.lookupClass == lookupClass) {
        PreevaluatedExpression realCachedSend = CompilerDirectives.unsafeCast(cachedSend, PreevaluatedExpression.class, true);
        return realCachedSend.doPreEvaluated(frame, SArray.fromSArrayToArgArrayWithReceiver(argsArr, receiver));
      } else {
        return nextInCache.executeDispatch(frame, receiver, selector, lookupClass, argsArr);
      }
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1 + nextInCache.lengthOfDispatchChain();
    }
  }

  private static final class GenericDispatchNode extends AbstractSymbolSuperDispatch {

    public GenericDispatchNode(final boolean executesEnforced, final boolean alwaysEnforced) {
      super(executesEnforced, alwaysEnforced);
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object receiver, final SSymbol selector, final SClass lookupClass,
        final Object[] argsArr) {
      SInvokable invokable = lookupClass.lookupInvokable(selector);

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
