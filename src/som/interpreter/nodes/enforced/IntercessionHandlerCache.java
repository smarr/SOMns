package som.interpreter.nodes.enforced;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.SArguments;
import som.interpreter.nodes.GlobalNode;
import som.interpreter.nodes.GlobalNode.UninitializedGlobalReadNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.interpreter.nodes.dispatch.DispatchChain;
import som.interpreter.objectstorage.FieldAccessorNode;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractReadFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractWriteFieldNode;
import som.vm.Universe;
import som.vm.constants.Classes;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;


public final class IntercessionHandlerCache {
  public static final int INLINE_CACHE_SIZE = 6;

  public static AbstractIntercessionHandlerDispatch create(
      final String intercessionHandler, final boolean executesEnforced) {
    CompilerAsserts.neverPartOfCompilation("IntercessionHandlerCache.create");
    return new UninitializedDispatch(Universe.current().symbolFor(intercessionHandler), executesEnforced, 0);
  }

  public abstract static class AbstractIntercessionHandlerDispatch extends Node implements DispatchChain {
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

    private EnforcedMessageSendNode getOuterMessageSend() {
      Node node = determineChainHead(); // might still be `this`, just traverse the chain

      // now, let's just find the message send
      while (!(node instanceof EnforcedMessageSendNode)) {
        node = node.getParent();
      }

      return (EnforcedMessageSendNode) node;
    }

    private AbstractIntercessionHandlerDispatch specialize(
        final SObject rcvrDomain, final Object[] arguments) {
      transferToInterpreterAndInvalidate("Initialize a dispatch node.");

      SInvokable handler = rcvrDomain.getSOMClass().
          lookupInvokable(intercessionHandlerSelector);

      if (depth < INLINE_CACHE_SIZE) {
        AbstractIntercessionHandlerDispatch specialized;

        if (handler.getHolder() == Classes.domainClass) {
          switch (intercessionHandlerSelector.getString()) {
            case EnforcedFieldReadNode.INTERCESSION_SIGNATURE: {
              specialized = new StandardFieldRead(rcvrDomain,
                  intercessionHandlerSelector,
                  (int) (long) arguments[3] - 1, executesEnforced, depth);
              break;
            }
            case EnforcedFieldWriteNode.INTERCESSION_SIGNATURE: {
              specialized = new StandardFieldWrite(rcvrDomain,
                  intercessionHandlerSelector,
                  (int) (long) arguments[4] - 1, executesEnforced, depth);
              break;
            }
            case EnforcedMessageSendNode.INTERCESSION_SIGNATURE: {
              if (getOuterMessageSend().isSuperSend()) {
                specialized = new StandardMessageSend(rcvrDomain,
                    intercessionHandlerSelector,
                    (SClass) arguments[6], (SSymbol) arguments[3],
                    executesEnforced, depth);
              } else {
                specialized = new StandardMessageSend(rcvrDomain,
                    intercessionHandlerSelector,
                    (SSymbol) arguments[3], executesEnforced, depth);
              }
              break;
            }
            case EnforcedGlobalReadNode.INTERCESSION_SIGNATURE: {
              specialized = new StandardGlobalRead(rcvrDomain,
                  intercessionHandlerSelector, (SSymbol) arguments[3],
                  executesEnforced, depth);
              break;
            }
            default: {
              System.out.println("No standard domain support for: #" + intercessionHandlerSelector.getString());
              specialized = new CachedDispatch(rcvrDomain, handler,
                  executesEnforced, depth);
            }
          }
        } else {
          specialized = new CachedDispatch(rcvrDomain, handler,
            executesEnforced, depth);
        }

        return replace(specialized);
      }

      AbstractIntercessionHandlerDispatch headNode = determineChainHead();
      GenericDispatch generic = new GenericDispatch(intercessionHandlerSelector,
          executesEnforced);
      return headNode.replace(generic);
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final SObject rcvrDomain, final Object[] arguments) {
      return specialize(rcvrDomain, arguments).
          executeDispatch(frame, rcvrDomain, arguments);
    }

    private AbstractIntercessionHandlerDispatch determineChainHead() {
      Node i = this;
      while (i.getParent() instanceof AbstractIntercessionHandlerDispatch) {
        i = i.getParent();
      }
      return (AbstractIntercessionHandlerDispatch) i;
    }

    @Override
    public int lengthOfDispatchChain() {
      return 0;
    }
  }

  private abstract static class AbstractChainDispatch extends AbstractIntercessionHandlerDispatch {
    @Child private AbstractIntercessionHandlerDispatch next;
    private final SObject rcvrDomain;

    public AbstractChainDispatch(final SObject rcvrDomain,
        final SSymbol intercessionHandler, final boolean executesEnforced,
        final int depth) {
      super(executesEnforced, depth);
      next = new UninitializedDispatch(intercessionHandler,
          executesEnforced, depth + 1);
      this.rcvrDomain = rcvrDomain;
    }

    public abstract Object doDispatch(final VirtualFrame frame,
        final Object[] arguments);

    @Override
    public final Object executeDispatch(final VirtualFrame frame,
        final SObject rcvrDomain, final Object[] arguments) {
      if (this.rcvrDomain == rcvrDomain) {
        return doDispatch(frame, arguments);
      } else {
        return next.executeDispatch(frame, rcvrDomain, arguments);
      }
    }

    @Override
    public final int lengthOfDispatchChain() {
      return 1 + next.lengthOfDispatchChain();
    }
  }

  public static final class CachedDispatch extends AbstractChainDispatch {

    @Child private DirectCallNode dispatch;

    public CachedDispatch(final SObject rcvrDomain,
        final SInvokable intercessionHandler, final boolean executesEnforced,
        final int depth) {
      super(rcvrDomain, intercessionHandler.getSignature(), executesEnforced, depth);
      this.dispatch = Truffle.getRuntime().createDirectCallNode(
          intercessionHandler.getCallTarget(executesEnforced));
    }

    @Override
    public Object doDispatch(final VirtualFrame frame, final Object[] arguments) {
      return dispatch.call(frame, arguments);
    }
  }

  private static final class StandardFieldRead extends AbstractChainDispatch {
    @Child private AbstractReadFieldNode read;

    private StandardFieldRead(final SObject rcvrDomain,
        final SSymbol intercessionHandler, final int fieldIndex,
        final boolean executesEnforced, final int depth) {
      super(rcvrDomain, intercessionHandler, executesEnforced, depth);
      this.read = FieldAccessorNode.createRead(fieldIndex);
    }

    @Override
    public Object doDispatch(final VirtualFrame frame, final Object[] arguments) {
      assert arguments[4] instanceof SObject;
      SObject obj = CompilerDirectives.unsafeCast(arguments[4], SObject.class, true, true);
      return read.read(obj);
    }
  }

  private static final class StandardFieldWrite extends AbstractChainDispatch {
    @Child private AbstractWriteFieldNode write;

    private StandardFieldWrite(final SObject rcvrDomain,
        final SSymbol intercessionHandler, final int fieldIndex,
        final boolean executesEnforced, final int depth) {
      super(rcvrDomain, intercessionHandler, executesEnforced, depth);
      this.write = FieldAccessorNode.createWrite(fieldIndex);
    }

    @Override
    public Object doDispatch(final VirtualFrame frame, final Object[] arguments) {
      assert arguments[3] != null;
      assert arguments[5] instanceof SObject;
      Object val  = CompilerDirectives.unsafeCast(arguments[3],  Object.class, true, true);
      SObject obj = CompilerDirectives.unsafeCast(arguments[5], SObject.class, true, true);
      return write.write(obj, val);
    }
  }

  private static final class StandardGlobalRead extends AbstractChainDispatch {
    @Child private GlobalNode global;

    private StandardGlobalRead(final SObject rcvrDomain,
        final SSymbol intercessionHandler, final SSymbol globalName,
        final boolean executesEnforced, final int depth) {
      super(rcvrDomain, intercessionHandler, executesEnforced, depth);
      this.global = new UninitializedGlobalReadNode(globalName, null, executesEnforced);
    }

    @Override
    public Object doDispatch(final VirtualFrame frame, final Object[] arguments) {
      return global.executeGeneric(frame);
    }
  }

  private static final class StandardMessageSend extends AbstractChainDispatch {
    @Child private AbstractMessageSendNode send;

    private StandardMessageSend(final SObject rcvrDomain,
        final SSymbol intercessionHandler, final SSymbol selector,
        final boolean executesEnforced, final int depth) {
      super(rcvrDomain, intercessionHandler, executesEnforced, depth);
      send = MessageSendNode.createForStandardDomainHandler(selector,
          getSourceSection(), executesEnforced, null);
    }

    private StandardMessageSend(final SObject rcvrDomain,
        final SSymbol intercessionHandler, final SClass lookupClass,
        final SSymbol selector, final boolean executesEnforced, final int depth) {
      super(rcvrDomain, intercessionHandler, executesEnforced, depth);
      send = MessageSendNode.createForStandardDomainHandler(selector,
          getSourceSection(), executesEnforced, lookupClass);
    }

    @Override
    public Object doDispatch(final VirtualFrame frame, final Object[] arguments) {
      assert arguments[4] instanceof Object[];
      assert arguments[5] != null;
      Object[] somArr = CompilerDirectives.unsafeCast(arguments[4], Object[].class, true, true);
      Object   rcvr   = CompilerDirectives.unsafeCast(arguments[5],   Object.class, true, true);
      Object[] args = SArray.fromSArrayToArgArrayWithReceiver(somArr, rcvr);
      return send.doPreEvaluated(frame, args);
    }
  }

  private static final class GenericDispatch extends AbstractIntercessionHandlerDispatch {
    private final SSymbol intercessionHandlerSelector;

    public GenericDispatch(final SSymbol intercessionHandlerSelector,
        final boolean executesEnforced) {
      super(executesEnforced, 0);
      this.intercessionHandlerSelector = intercessionHandlerSelector;
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final SObject rcvrDomain, final Object[] arguments) {
      CompilerAsserts.neverPartOfCompilation("IntercessionHandlerCache.generic"); // no caching, direct invokes, no loop count reporting...

      SObject currentDomain = SArguments.domain(frame);
      SInvokable handler = rcvrDomain.getSOMClass().
          lookupInvokable(intercessionHandlerSelector);
      return handler.invoke(currentDomain, false, arguments);
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1000;
    }
  }
}
