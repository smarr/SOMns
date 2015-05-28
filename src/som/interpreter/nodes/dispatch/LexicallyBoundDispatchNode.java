package som.interpreter.nodes.dispatch;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.compiler.AccessModifier;
import som.compiler.ClassBuilder.ClassDefinitionId;
import som.interpreter.Types;
import som.interpreter.nodes.MessageSendNode.GenericMessageSendNode;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;


/**
 * Dispatch node for outer sends (name based on Newspeak spec), which includes
 * self sends (i.e., outer sends with a degree k=0.).
 */
public final class LexicallyBoundDispatchNode extends AbstractDispatchWithLookupNode {
  private final ClassDefinitionId classForPrivateLookup;

  public LexicallyBoundDispatchNode(final SSymbol selector,
      final ClassDefinitionId classForPrivateLookup) {
    super(selector);
    this.classForPrivateLookup = classForPrivateLookup;
  }

  // TODO: refactor, needs to be folded with the normal UninitializedDispatchNode
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
      Dispatchable dispatchable = rcvrClass.lookupPrivate(selector, classForPrivateLookup);

      if (dispatchable != null && dispatchable.getAccessModifier() == AccessModifier.PRIVATE) {
        System.out.println("TODO: We might replace the whole dispatch chain, and link this unconditionally");
      }

      LexicallyBoundDispatchNode newChainEnd = new LexicallyBoundDispatchNode(
          selector, classForPrivateLookup);

      if (rcvr instanceof SObject) {
        AbstractDispatchNode node;
        if (dispatchable != null) {
          node = dispatchable.getDispatchNode(rcvr, rcvrClass, newChainEnd);
        } else {
          node = new CachedDnuSObjectCheckNode(rcvrClass, selector, newChainEnd);
        }

        if ((getParent() instanceof CachedDispatchSObjectCheckNode)) {
          return replace(node);
        } else {
          SObjectCheckDispatchNode checkNode = new SObjectCheckDispatchNode(node,
              new LexicallyBoundDispatchNode(selector, classForPrivateLookup));
          return replace(checkNode);
        }
      } else {
        AbstractDispatchNode next = sendNode.getDispatchListHead();

        AbstractDispatchNode node;
        if (dispatchable == null) {
          node = new CachedDnuSimpleCheckNode(
              rcvr.getClass(), rcvrClass, selector, next);
        } else {
          node = dispatchable.getDispatchNode(rcvr, rcvr.getClass(), next);
        }

        // the simple checks are prepended
        sendNode.adoptNewDispatchListHead(node);
        return node;
      }
    }

    // the chain is longer than the maximum defined by INLINE_CACHE_SIZE and
    // thus, this callsite is considered to be megamorphic, and we generalize
    // it.
    GenericDispatchNode genericReplacement = new GenericDispatchNode(selector,
        AccessModifier.PRIVATE, classForPrivateLookup);
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
