package som.primitives.reflection;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.Types;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.PreevaluatedExpression;
import som.interpreter.nodes.dispatch.DispatchChain;
import som.primitives.arrays.ToArgumentsArrayNode;
import som.primitives.arrays.ToArgumentsArrayNodeFactory;
import som.vmobjects.SArray;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;


public abstract class AbstractSymbolDispatch extends Node implements DispatchChain {
  public static final int INLINE_CACHE_SIZE = 6;

  public static AbstractSymbolDispatch create() {
    return new UninitializedDispatchNode(0);
  }

  protected final int depth;

  public AbstractSymbolDispatch(final int depth) {
    this.depth = depth;
  }

  public abstract Object executeDispatch(VirtualFrame frame, Object receiver,
      SSymbol selector, SArray argsArr);

  private static final class UninitializedDispatchNode extends AbstractSymbolDispatch {

    public UninitializedDispatchNode(final int depth) {
      super(depth);
    }

    private AbstractSymbolDispatch specialize(final SSymbol selector) {
      transferToInterpreterAndInvalidate("Initialize a dispatch node.");

      if (depth < INLINE_CACHE_SIZE) {
        CachedDispatchNode specialized = new CachedDispatchNode(selector,
            new UninitializedDispatchNode(depth + 1), depth);
        return replace(specialized);
      }

      AbstractSymbolDispatch headNode = determineChainHead();
      GenericDispatchNode generic = new GenericDispatchNode();
      return headNode.replace(generic);
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object receiver, final SSymbol selector, final SArray argsArr) {
      return specialize(selector).
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
    @Child private ToArgumentsArrayNode toArgArray;

    public CachedDispatchNode(final SSymbol selector,
        final AbstractSymbolDispatch nextInCache,
        final int depth) {
      super(depth);
      this.selector = selector;
      this.nextInCache = nextInCache;
      cachedSend = MessageSendNode.createForPerformNodes(selector);
      toArgArray = ToArgumentsArrayNodeFactory.create(null, null);
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object receiver, final SSymbol selector, final SArray argsArr) {
      if (this.selector == selector) {
        Object[] arguments = toArgArray.executedEvaluated(argsArr, receiver);

        PreevaluatedExpression realCachedSend = (PreevaluatedExpression) cachedSend;
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

    @Child private IndirectCallNode call;
    @Child private ToArgumentsArrayNode toArgArray;

    public GenericDispatchNode() {
      super(0);
      call = Truffle.getRuntime().createIndirectCallNode();
      toArgArray = ToArgumentsArrayNodeFactory.create(null, null);
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object receiver, final SSymbol selector, final SArray argsArr) {
      SInvokable invokable = Types.getClassOf(receiver).lookupInvokable(selector);

      Object[] arguments = toArgArray.executedEvaluated(argsArr, receiver);

      return call.call(frame, invokable.getCallTarget(), arguments);
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1000;
    }
  }
}
