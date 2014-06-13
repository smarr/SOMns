package som.primitives.reflection;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.SArguments;
import som.interpreter.Types;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vm.Universe;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
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
    @Child protected AbstractMessageSendNode cachedSend;
    @Child protected AbstractSymbolDispatch nextInCache;

    public CachedDispatchNode(final SSymbol selector,
        final AbstractSymbolDispatch nextInCache) {
      this.selector = selector;
      this.nextInCache = nextInCache;
      cachedSend = MessageSendNode.createForPerformNodes(selector);
    }

    @ExplodeLoop
    private Object[] mergeReceiverWithArguments(final Object receiver, final Object[] argsArray) {
      Object[] arguments = new Object[argsArray.length + 1];
      arguments[0] = receiver;
      for (int i = 0; i < argsArray.length; i++) {
        arguments[i + 1] = argsArray[i];
      }
      return arguments;
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object receiver, final SSymbol selector, final Object[] argsArr) {
      if (this.selector == selector) {
        return cachedSend.doPreEvaluated(frame, mergeReceiverWithArguments(receiver, argsArr));
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

      SObject domain = SArguments.domain(frame);
      boolean enforced = SArguments.enforced(frame);

      Object[] args = SArguments.createSArgumentsWithReceiver(domain, enforced, receiver, argsArr);

      return invokable.invokeWithSArguments(args);
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1000;
    }
  }
}
