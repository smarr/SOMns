package som.primitives.reflection;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.SArguments;
import som.interpreter.Types;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.PreevaluatedExpression;
import som.vm.Universe;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;


public abstract class AbstractSymbolDispatch extends Node {
  public static final int INLINE_CACHE_SIZE = 6;

  public static AbstractSymbolDispatch create() {
    return new UninitializedDispatchNode();
  }

  public abstract Object executeDispatch(VirtualFrame frame, Object receiver,
      SSymbol selector, Object[] argsArr);

  public abstract int lengthOfDispatchChain();

  private static final class UninitializedDispatchNode extends AbstractSymbolDispatch {
    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object receiver, final SSymbol selector, final Object[] argsArr) {
      transferToInterpreterAndInvalidate("Initialize a dispatch node.");

      int chainDepth = determineChainLength();

      if (chainDepth < INLINE_CACHE_SIZE) {
        CachedDispatchNode specialized = new CachedDispatchNode(selector,
            new UninitializedDispatchNode());
        return replace(specialized).executeDispatch(frame, receiver, selector, argsArr);
      }

      // TODO: normally, we throw away the whole chain, and replace it with the megamorphic node...
      GenericDispatchNode generic = new GenericDispatchNode();
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
        final AbstractSymbolDispatch nextInCache) {
      this.selector = selector;
      this.nextInCache = nextInCache;
      cachedSend = MessageSendNode.createForPerformNodes(selector);
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object receiver, final SSymbol selector, final Object[] argsArr) {
      if (this.selector == selector) {
        Object[] arguments = SArguments.createSArgumentsArrayFrom(receiver, argsArr);

        PreevaluatedExpression realCachedSend = CompilerDirectives.unsafeCast(cachedSend, PreevaluatedExpression.class, true);
        return realCachedSend.doPreEvaluated(frame, arguments);
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

    public GenericDispatchNode() {
      universe = Universe.current();
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object receiver, final SSymbol selector, final Object[] argsArr) {
      SInvokable invokable = Types.getClassOf(receiver, universe).lookupInvokable(selector);

      Object[] args = SArguments.createSArgumentsArrayFrom(receiver, argsArr);

      return invokable.invoke(args);
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1000;
    }
  }
}
