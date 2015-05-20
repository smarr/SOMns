package som.interpreter.nodes.dispatch;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.compiler.AccessModifier;
import som.interpreter.Types;
import som.interpreter.nodes.MessageSendNode.GenericMessageSendNode;
import som.interpreter.nodes.dispatch.CachedDispatchSimpleCheckNode.CachedDispatchFalseCheckNode;
import som.interpreter.nodes.dispatch.CachedDispatchSimpleCheckNode.CachedDispatchTrueCheckNode;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;


public final class UninitializedDispatchNode extends AbstractDispatchWithLookupNode {

  private final AccessModifier minimalVisibility;

  public UninitializedDispatchNode(final SSymbol selector,
      final AccessModifier minimalVisibility) {
    super(selector);
    this.minimalVisibility = minimalVisibility;
  }

  private AbstractDispatchNode specialize(final Object[] arguments) {
    transferToInterpreterAndInvalidate("Initialize a dispatch node.");
    Object rcvr = arguments[0];

    assert rcvr != null;

    // Determine position in dispatch node chain, i.e., size of inline cache
    Node i = this;
    int chainDepth = 0;
    while (i.getParent() instanceof AbstractDispatchNode) {
      i = i.getParent();
      chainDepth++;
    }
    GenericMessageSendNode sendNode = (GenericMessageSendNode) i.getParent();

    if (chainDepth < INLINE_CACHE_SIZE) {
      SClass rcvrClass = Types.getClassOf(rcvr);
      SInvokable method = rcvrClass.lookupInvokable(selector, minimalVisibility);

      CallTarget callTarget;
      if (method != null) {
        if (method.getAccessModifier() == AccessModifier.PRIVATE) {
          System.out.println("TODO: We might replace the whole dispatch chain, and link this unconditionally");
        }
        callTarget = method.getCallTarget();
      } else {
        callTarget = null;
      }

      UninitializedDispatchNode newChainEnd = new UninitializedDispatchNode(
          selector, minimalVisibility);

      if (rcvr instanceof SObject) {
        AbstractCachedDispatchNode node;
        if (method != null) {
          node = new CachedDispatchSObjectCheckNode(
              rcvrClass, callTarget, newChainEnd);
        } else {
          node = new CachedDnuSObjectCheckNode(rcvrClass, selector, newChainEnd);
        }

        if ((getParent() instanceof CachedDispatchSObjectCheckNode)) {
          return replace(node);
        } else {
          SObjectCheckDispatchNode checkNode = new SObjectCheckDispatchNode(node,
              new UninitializedDispatchNode(selector, minimalVisibility));
          return replace(checkNode);
        }
      } else {
        AbstractDispatchNode next = sendNode.getDispatchListHead();

        AbstractCachedDispatchNode node;
        if (method == null) {
          node = new CachedDnuSimpleCheckNode(
              rcvr.getClass(), rcvrClass, selector, next);
        } else {
          if (rcvr == Boolean.TRUE) {
            node = new CachedDispatchTrueCheckNode(callTarget, next);
          } else if (rcvr == Boolean.FALSE) {
            node = new CachedDispatchFalseCheckNode(callTarget, next);
          } else {
            node = new CachedDispatchSimpleCheckNode(
                  rcvr.getClass(), callTarget, next);
          }
        }

        // the simple checks are prepended
        sendNode.adoptNewDispatchListHead(node);
        return node;
      }
    }

    // the chain is longer than the maximum defined by INLINE_CACHE_SIZE and
    // thus, this callsite is considered to be megaprophic, and we generalize
    // it.
    GenericDispatchNode genericReplacement = new GenericDispatchNode(selector);
    sendNode.replaceDispatchListHead(genericReplacement);
    return genericReplacement;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    return specialize(arguments).
        executeDispatch(frame, arguments);
  }

  @Override
  public int lengthOfDispatchChain() {
    return 0;
  }

}
